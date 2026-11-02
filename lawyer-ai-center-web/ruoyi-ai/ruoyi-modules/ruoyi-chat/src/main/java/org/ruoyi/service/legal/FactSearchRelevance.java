package org.ruoyi.service.legal;

import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import java.util.*;
import java.util.regex.Pattern;

/** Small, deterministic ranking layer for fact search only; never manufactures references. */
public final class FactSearchRelevance {
    private FactSearchRelevance() {}
    private record Domain(String name,List<String> signals,List<String> recall) {}
    private record Focus(String name,String domain,List<String> triggers,List<String> matches,double weight) {}
    private static final List<Domain> DOMAINS=List.of(
        new Domain("借贷",List.of("借贷","借款","借条","出借","还款","本金"),List.of("借款","借贷","还款","返还借款")),
        new Domain("劳动",List.of("劳动","工资","欠薪","用人单位","经济补偿","辞退"),List.of("劳动","工资","劳动报酬","经济补偿","解除劳动合同")),
        new Domain("买卖",List.of("买卖","交货","货款","设备采购","购买设备"),List.of("买卖","交付","出卖人","买受人","货款")),
        new Domain("租赁",List.of("租赁","出租人","承租人","租金","房屋维修"),List.of("租赁","出租人","承租人","租金","维修"))
    );
    // Match independent disputes, rather than rewarding repeated generic domain words in long notes.
    // No law/article whitelist: these expressions also work for new readable knowledge documents.
    private static final List<Focus> FOCUSES=List.of(
        new Focus("到期还本","借贷",List.of("还款","本金","返还借款","到期"),List.of("期限返还借款","到期返还借款","返还本金","还款期限"),1.2),
        new Focus("借贷计息","借贷",List.of("利息","利率","月息"),List.of("利息","利率","计息","高利"),0.9),
        new Focus("逾期利息","借贷",List.of("逾期利息"),List.of("逾期利息"),0.8),
        new Focus("工资支付","劳动",List.of("工资","劳动报酬","欠薪"),List.of("工资","劳动报酬","欠薪"),1.0),
        new Focus("劳动解除","劳动",List.of("解除","辞退","经济补偿"),List.of("解除劳动合同","解除或者终止劳动合同","经济补偿","违法解除"),1.0),
        new Focus("交付","买卖",List.of("交货","交付","未交"),List.of("交付","交货"),0.9),
        new Focus("买卖解除","买卖",List.of("解除","催告"),List.of("解除合同","合同解除","合同的解除","迟延履行","拒不交付"),1.2),
        new Focus("价款返还","买卖",List.of("返还货款","返还价款"),List.of("恢复原状","返还货款","返还价款"),1.2),
        new Focus("维修","租赁",List.of("维修","修缮","拒修"),List.of("维修","修缮"),1.4),
        new Focus("租金减免","租赁",List.of("减免","减少租金"),List.of("减少租金","减免相应租金","减免租金"),1.0)
    );
    private static final Set<String> GENERIC=Set.of("合同","违约","解除","赔偿","支付","请求","纠纷","责任","争议","法律","依据");
    private static String text(String value){return Objects.toString(value,"");}
    private static String hints(FactElements e){return String.join(" ",e.causeHints())+" "+String.join(" ",e.keywords())+" "+String.join(" ",e.disputes());}
    private static List<Domain> domains(FactElements e){String value=hints(e);return DOMAINS.stream().filter(d->d.signals().stream().anyMatch(value::contains)).toList();}
    private static List<Focus> focuses(FactElements e){
        String value=hints(e);var names=domains(e).stream().map(Domain::name).toList();
        return FOCUSES.stream().filter(f->names.contains(f.domain())&&f.triggers().stream().anyMatch(value::contains)).toList();
    }
    public static List<String> terms(FactElements e){
        var terms=new LinkedHashSet<String>();
        // The original extracted terms remain first, with bounded lexical expansions for recall.
        for(String value:e.keywords())if(!value.isBlank()&&!GENERIC.contains(value))terms.add(value);
        for(String value:e.causeHints())if(!value.isBlank())terms.add(value);
        terms.addAll(e.lawsHints());
        for(var domain:domains(e))terms.addAll(domain.recall());
        for(var focus:focuses(e))terms.addAll(focus.matches());
        String input=hints(e);
        for(String term:List.of("本金","到期","返还","借期利息","逾期利息","利率","利息","催告","迟延","交付","解除","工资","补偿","维修","费用","减免"))if(input.contains(term))terms.add(term);
        if(terms.isEmpty())terms.addAll(e.keywords());
        return terms.stream().filter(t->t.length()>=2&&t.length()<=60).limit(32).toList();
    }
    public static String query(FactElements e){return String.join(" ",terms(e));}
    public record Explanation(double total,double original,double overlap,double domain,double focus,double metadata,List<String> matchedTerms,List<String> matchedFocus,List<String> inputDomains) {}
    public static Explanation explain(FactElements elements,KnowledgeRetrievalVo hit){
        var ref=hit.getReference();String title=text(hit.getSourceName());
        String law=ref==null?"":text(ref.lawName()),article=ref==null?"":text(ref.articleNo());
        String heading=title+" "+law+" "+article,body=text(hit.getContent()),all=heading+" "+body;
        double score=hit.getScore()==null||!Double.isFinite(hit.getScore())?0:hit.getScore();
        // Scores from unrelated libraries/models are not calibrated; keep them only as a weak tie-breaker.
        double original=Math.max(0,Math.min(1,score))*0.12,overlap=0;
        var matched=new ArrayList<String>();
        for(String term:terms(elements))if(!GENERIC.contains(term)&&all.contains(term)){
            matched.add(term);overlap+=heading.contains(term)?0.18:0.10;
        }
        overlap=Math.min(1.2,overlap);
        var inputDomains=domains(elements);double domain=0;
        for(var d:inputDomains){
            long count=d.recall().stream().filter(all::contains).count();
            domain+=Math.min(0.60,count*0.15);
        }
        // Penalise a clearly different named topic only for unambiguous single-domain requests.
        // Mixed labour/loan facts deliberately keep both domains; this is never an access/filter rule.
        if(inputDomains.size()==1){
            var expected=inputDomains.get(0);
            for(var other:DOMAINS)if(other!=expected&&other.signals().stream().anyMatch(heading::contains))domain-=0.8;
        }
        double focus=0;var matchedFocus=new ArrayList<String>();
        for(var intent:focuses(elements))if(intent.matches().stream().anyMatch(all::contains)){
            focus+=intent.weight();matchedFocus.add(intent.name());
        }
        focus=Math.min(3.2,focus);
        double metadata=0;
        for(String name:elements.lawsHints())if(!name.isBlank()&&law.contains(name))metadata+=0.2;
        var articlePattern=Pattern.compile("第[零〇一二三四五六七八九十百千万0-9]+条");
        var requested=articlePattern.matcher(hints(elements));
        while(requested.find())if(heading.contains(requested.group()))metadata+=0.25;
        metadata=Math.min(0.45,metadata);
        return new Explanation(original+overlap+domain+focus+metadata,original,overlap,domain,focus,metadata,List.copyOf(matched),List.copyOf(matchedFocus),inputDomains.stream().map(Domain::name).toList());
    }
    public static List<KnowledgeRetrievalVo> rank(FactElements elements,List<KnowledgeRetrievalVo> hits){
        // TimSort preserves the original retrieval order for equal evidence. No refs/score mutations.
        var scores=new IdentityHashMap<KnowledgeRetrievalVo,Double>();
        for(var hit:hits)scores.put(hit,explain(elements,hit).total());
        return hits.stream().sorted(Comparator.comparingDouble((KnowledgeRetrievalVo hit)->scores.get(hit)).reversed()).toList();
    }
}
