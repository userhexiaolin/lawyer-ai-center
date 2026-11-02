package org.ruoyi.service.legal;

import org.apache.poi.xwpf.usermodel.*;
import org.w3c.dom.*;
import java.io.*;
import java.util.*;

/** Edits text nodes in an original DOCX package; never reconstructs its page or style definitions. */
public final class TemplateLayout {
    private TemplateLayout(){}
    private static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final java.util.regex.Pattern BLANK_MARKER=java.util.regex.Pattern.compile("\\{\\{[^{}\\r\\n]{1,200}}}|【[^【】\\r\\n]{0,80}(?:待补充|待填写|待确认|待核实|未提供)[^【】\\r\\n]{0,80}】");
    public static String presentBlanks(String text){return BLANK_MARKER.matcher(text).replaceAll(match->" ".repeat(match.group().length()));}
    /** Output copy only, including markers split across runs, tables, headers and footers. */
    public static byte[] presentBlanks(byte[] bytes){
        try(var doc=new XWPFDocument(new ByteArrayInputStream(bytes));var out=new ByteArrayOutputStream()){
            for(Node p:paragraphs(doc)){
                var matcher=BLANK_MARKER.matcher(text(p));List<int[]> ranges=new ArrayList<>();
                while(matcher.find())ranges.add(new int[]{matcher.start(),matcher.end()});
                for(int i=ranges.size()-1;i>=0;i--)replaceRange(p,ranges.get(i)[0],ranges.get(i)[1]," ".repeat(ranges.get(i)[1]-ranges.get(i)[0]));
            }
            doc.write(out);return out.toByteArray();
        }catch(Exception e){throw new DraftingException("无法生成草稿空白显示，原草稿仍保留",422);}
    }
    public record Block(String id,String text,boolean editable,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<TemplateFields.Field> fields){
        public Block(String id,String text,boolean editable){this(id,text,editable,null);}
    }
    private static final String FIELD_PART="/customXml/yulvxing-template-fields.json";
    public static byte[] storeFields(byte[] docx,List<Block> blocks){
        try(var doc=new XWPFDocument(new ByteArrayInputStream(docx));var out=new ByteArrayOutputStream()){
            var name=org.apache.poi.openxml4j.opc.PackagingURIHelper.createPartName(FIELD_PART);
            var pkg=doc.getPackage();if(pkg.containPart(name))pkg.removePart(name);
            var part=pkg.createPart(name,"application/json");
            try(var stream=part.getOutputStream()){new com.fasterxml.jackson.databind.ObjectMapper().writeValue(stream,blocks);}
            doc.write(out);return out.toByteArray();
        }catch(Exception e){throw new DraftingException("模板字段配置存储失败，原版本未修改",500);}
    }
    public static List<Block> storedFields(byte[] docx,List<Block> fallback){
        try(var doc=new XWPFDocument(new ByteArrayInputStream(docx))){
            var name=org.apache.poi.openxml4j.opc.PackagingURIHelper.createPartName(FIELD_PART);
            if(!doc.getPackage().containPart(name))return fallback;
            try(var stream=doc.getPackage().getPart(name).getInputStream()){
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(stream,new com.fasterxml.jackson.core.type.TypeReference<List<Block>>(){});
            }
        }catch(Exception e){throw new DraftingException("模板字段配置损坏，请重新识别",422);}
    }
    private static final String STATE_PART="/customXml/yulvxing-template-state.json";
    public record State(boolean raw,boolean confirmed,String method,String warning){}
    public static State state(byte[] docx){
        // State lives in a small custom part; do not instantiate the entire Word document to read it.
        try(var pkg=org.apache.poi.openxml4j.opc.OPCPackage.open(new ByteArrayInputStream(docx))){
            var name=org.apache.poi.openxml4j.opc.PackagingURIHelper.createPartName(STATE_PART);
            if(!pkg.containPart(name))return null;
            try(var input=pkg.getPart(name).getInputStream()){return new com.fasterxml.jackson.databind.ObjectMapper().readValue(input,State.class);}
        }catch(Exception e){throw new DraftingException("模板解析状态损坏，请重新上传",422);}
    }
    public static byte[] storeState(byte[] docx,State state){
        try(var doc=new XWPFDocument(new ByteArrayInputStream(docx));var out=new ByteArrayOutputStream()){
            var name=org.apache.poi.openxml4j.opc.PackagingURIHelper.createPartName(STATE_PART);
            var pkg=doc.getPackage();if(pkg.containPart(name))pkg.removePart(name);
            try(var stream=pkg.createPart(name,"application/json").getOutputStream()){new com.fasterxml.jackson.databind.ObjectMapper().writeValue(stream,state);}
            doc.write(out);return out.toByteArray();
        }catch(Exception e){throw new DraftingException("模板解析状态保存失败",500);}
    }
    /** Markdown carries semantic structure; Word-specific font sizes and pagination stay in DOCX/PDF. */
    public static String markdown(byte[] filled){
        try(var doc=new XWPFDocument(new ByteArrayInputStream(filled))){
            StringBuilder result=new StringBuilder();
            for(var header:doc.getHeaderList())markdown(header.getBodyElements(),result);
            markdown(doc.getBodyElements(),result);
            for(var footer:doc.getFooterList())markdown(footer.getBodyElements(),result);
            return result.toString().strip();
        }catch(Exception e){throw new DraftingException("无法导出模板的Markdown结构",422);}
    }
    private static void markdown(List<IBodyElement> elements,StringBuilder out){
        for(var element:elements){
            if(element instanceof XWPFParagraph paragraph){
                String value=markdown(paragraph);if(!value.isBlank())out.append(value).append("\n\n");
            }else if(element instanceof XWPFTable table){
                int columns=table.getRows().stream().mapToInt(r->r.getTableCells().size()).max().orElse(0);
                if(columns==0)continue;
                for(int row=0;row<table.getRows().size();row++){
                    var cells=table.getRows().get(row).getTableCells();out.append('|');
                    for(int column=0;column<columns;column++){
                        StringBuilder cell=new StringBuilder();if(column<cells.size())markdown(cells.get(column).getBodyElements(),cell);
                        out.append(' ').append(cell.toString().strip().replace("\n\n","<br>").replace("\n","<br>")).append(" |");
                    }
                    out.append('\n');
                    if(row==0){out.append('|');for(int column=0;column<columns;column++)out.append(" --- |");out.append('\n');}
                }
                out.append('\n');
            }
        }
    }
    private static String markdown(XWPFParagraph paragraph){
        StringBuilder out=new StringBuilder();String style=Objects.toString(paragraph.getStyle(),"");
        var heading=java.util.regex.Pattern.compile("(?i)(?:heading|标题)\\s*([1-6])").matcher(style);
        if(heading.matches())out.append("#".repeat(Integer.parseInt(heading.group(1)))).append(' ');
        else if(paragraph.getNumID()!=null)out.append("bullet".equals(paragraph.getNumFmt())?"- ":"1. ");
        for(var run:paragraph.getRuns()){
            String value=run.text();if(value.isEmpty())continue;
            String escaped=value.replace("\\","\\\\").replace("*","\\*").replace("_","\\_").replace("|","\\|").replace("[","\\[").replace("]","\\]");
            if(!value.isBlank()){if(run.isBold())escaped="**"+escaped+"**";if(run.isItalic())escaped="*"+escaped+"*";}
            out.append(escaped.replace("\n","  \n"));
        }
        // Text boxes are nested paragraphs, outside XWPFParagraph#getRuns' own text.
        List<Node> nested=new ArrayList<>();walk(paragraph.getCTP().getDomNode(),nested);
        for(int i=1;i<nested.size();i++){String value=text(nested.get(i));if(!value.isBlank())out.append("\n\n").append(value);}
        return out.toString();
    }
    public static void validateEditable(byte[] source,Set<String> selected){
        try(var doc=new XWPFDocument(new ByteArrayInputStream(source))){
            var paragraphs=paragraphs(doc);
            for(int index=0;index<paragraphs.size();index++)if(selected.contains("p"+index)){
                Node p=paragraphs.get(index);List<Node> nodes=new ArrayList<>();texts(p,nodes);
                var ranges=TemplateFields.ranges(text(p));
                for(int[] range:ranges){Set<String> styles=new HashSet<>();int position=0;for(Node t:nodes){int end=position+value(t).length();if(end>range[0]&&position<range[1])styles.add(runStyle(t));position=end;}
                    if(styles.size()>1)throw new DraftingException("填写区域含不同字体、字号或强调格式，请为可变文字设置同一格式的明确占位符后重新上传，避免错误继承字体",400);
                }
            }
        }catch(DraftingException e){throw e;}catch(Exception e){throw new DraftingException("无法校验填写区域的原始格式",400);}
    }
    private static String runStyle(Node text){
        Node run=text.getParentNode();while(run!=null&&!(W.equals(run.getNamespaceURI())&&"r".equals(run.getLocalName())))run=run.getParentNode();
        SortedMap<String,String> properties=new TreeMap<>();
        if(run!=null)for(Node child=run.getFirstChild();child!=null;child=child.getNextSibling())if(W.equals(child.getNamespaceURI())&&"rPr".equals(child.getLocalName()))for(Node property=child.getFirstChild();property!=null;property=property.getNextSibling()){
            if(property.getNodeType()!=Node.ELEMENT_NODE||property.getLocalName()==null)continue;
            properties.put(property.getLocalName(),"");var attributes=property.getAttributes();if(attributes!=null)for(int i=0;i<attributes.getLength();i++){Node a=attributes.item(i);properties.put(property.getLocalName()+":"+a.getLocalName(),a.getNodeValue());}
        }
        return properties.toString();
    }
    public static void validateReplacement(String original,String replacement){
        TemplateFields.values(original,replacement);
    }
    public static void validateReplacement(Block original,String replacement){TemplateFields.values(original,replacement);}
    public static List<Block> inspect(byte[] bytes){
        return inspect(bytes,false);
    }
    public static List<Block> inspect(byte[] bytes,boolean identifyFields){
        try(var doc=new XWPFDocument(new ByteArrayInputStream(bytes))){
            List<Block> result=new ArrayList<>();int i=0;String previous="";
            for(Node p:paragraphs(doc)) {
                String text=text(p),id="p"+i;
                boolean emptyCell=identifyFields&&text.isEmpty()&&W.equals(p.getParentNode().getNamespaceURI())&&"tc".equals(p.getParentNode().getLocalName())&&!hasFixedObjects(p);
                if(!text.isBlank()||emptyCell){
                    List<TemplateFields.Field> fields=null;
                    if(identifyFields){
                        // Keep real blanks with field semantics; whole form captions may anchor an insertion.
                        List<int[]> ranges=new ArrayList<>();
                        for(var range:TemplateFields.ranges(text)){
                            String raw=text.substring(range[0],range[1]);
                            if(raw.isEmpty()){
                                if(range[0]==text.length()&&!TemplateFields.captionLabel(text).isEmpty()&&!hasFixedObjects(p))ranges.add(range);
                                continue;
                            }
                            if(raw.isBlank()){
                                String left=text.substring(0,range[0]).stripTrailing();
                                if(left.endsWith("_")||left.endsWith("＿"))continue;
                                var inferred=TemplateFields.candidate(id,range[0],range[1],text,previous,false);
                                String right=text.substring(range[1]).stripLeading();
                                if(!inferred.enabled()||inferred.label().equals("填写项")||right.startsWith("□")||right.startsWith("☑")||right.startsWith("|"))continue;
                            }
                            ranges.add(range);
                        }
                        List<Node> ts=new ArrayList<>();texts(p,ts);int offset=0;
                        for(Node t:ts){String value=value(t);Node run=t.getParentNode();boolean underline=false;
                            for(Node child=run.getFirstChild();child!=null;child=child.getNextSibling())if("rPr".equals(child.getLocalName()))for(Node prop=child.getFirstChild();prop!=null;prop=prop.getNextSibling())if("u".equals(prop.getLocalName())&&(! (prop instanceof Element e)||!"none".equals(e.getAttributeNS(W,"val"))))underline=true;
                            if(underline&&value.matches("[ \\t　]+")){final int start=offset,end=offset+value.length();if(ranges.stream().noneMatch(r->r[0]<=start&&r[1]>=end))ranges.add(new int[]{start,end});}
                            offset+=value.length();
                        }
                        if(emptyCell)ranges.add(new int[]{0,0});ranges.sort(Comparator.comparingInt(r->r[0]));
                        // A visual blank can cross several Word runs or overlap a regex candidate.
                        List<int[]> merged=new ArrayList<>();
                        for(var range:ranges){
                            if(!merged.isEmpty()){
                                var lastRange=merged.get(merged.size()-1);
                                boolean touchingBlank=range[0]==lastRange[1]&&text.substring(lastRange[0],range[1]).isBlank();
                                if(range[0]<lastRange[1]||touchingBlank){lastRange[1]=Math.max(lastRange[1],range[1]);continue;}
                            }
                            merged.add(range.clone());
                        }
                        ranges=merged;
                        fields=new ArrayList<>();int last=-1;
                        for(var range:ranges){if(range[0]<last)continue;boolean underlined=false;int pos=0;
                            for(Node t:ts){int end=pos+value(t).length();if(end>range[0]&&pos<range[1]&&runStyle(t).contains("u:"))underlined=true;pos=end;}
                            var f=TemplateFields.candidate(id,range[0],range[1],text,previous,underlined);
                            if(emptyCell)f=new TemplateFields.Field(f.id(),id,0,0,"","表格填写项","text","","text",true);
                            fields.add(f);last=range[1];
                        }
                    }
                    result.add(new Block(id,text,false,fields));previous=text;
                }i++;
            }
            if(result.isEmpty()||result.size()>500)throw new DraftingException("模板须有1至500个非空正文段落，请精简后上传",400);
            return result;
        }catch(DraftingException e){throw e;}catch(Exception e){throw new DraftingException("无法读取模板版式，请检查DOCX文件",400);}
    }
    public static byte[] fill(byte[] source,List<Block> blocks){
        try(var doc=new XWPFDocument(new ByteArrayInputStream(source));var out=new ByteArrayOutputStream()){
            List<Node> ps=paragraphs(doc);Set<String> used=new HashSet<>();
            for(Block block:blocks){
                if(!block.id().matches("p\\d+")||!used.add(block.id()))throw new DraftingException("模板填写区域无效",422);
                int index=Integer.parseInt(block.id().substring(1));if(index>=ps.size())throw new DraftingException("模板填写区域已变化",422);
                Node p=ps.get(index);if(!text(p).equals(block.text())){validateReplacement(text(p),block.text());replace(p,block.text());}
            }
            doc.write(out);return out.toByteArray();
        }catch(DraftingException e){throw e;}catch(Exception e){throw new DraftingException("无法按原模板填写文档",422);}
    }
    public static byte[] fill(byte[] source,List<Block> filled,List<Block> originals){
        if(originals.stream().noneMatch(b->b.fields()!=null))return fill(source,filled);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(source));var out=new ByteArrayOutputStream()){
            var ps=paragraphs(doc);Map<String,Block> values=new HashMap<>();filled.forEach(b->values.put(b.id(),b));
            var logical=TemplateFields.logical(originals);
            for(int index=0;index<originals.size();index++){
                var original=originals.get(index);var value=values.get(original.id());
                if(value==null)throw new DraftingException("模板填写段落缺失",422);
                int paragraph=Integer.parseInt(original.id().substring(1));Node p=ps.get(paragraph);
                if(!text(p).equals(original.text()))throw new DraftingException("模板原文位置已变化，请重新识别字段",422);
                if(original.fields()==null){if(!original.text().equals(value.text())){validateReplacement(original.text(),value.text());replace(p,value.text());}continue;}
                var replacements=TemplateFields.values(logical.get(index),value.text());
                var fields=original.fields().stream().filter(TemplateFields.Field::enabled).toList();
                for(int j=fields.size()-1;j>=0;j--){var f=fields.get(j);String replacement=replacements.get(j);
                    if(replacement.equals("{{"+f.label()+"}}"))continue;
                    replaceField(p,f,replacement);
                }
            }
            doc.write(out);return out.toByteArray();
        }catch(DraftingException e){throw e;}catch(Exception e){throw new DraftingException("无法保留原模板填写字段，请检查原件及字段配置",422);}
    }
    private static void replaceField(Node p,TemplateFields.Field field,String replacement){
        if(field.presentation().equals("text")){if(field.start()==field.end()&&text(p).isEmpty()){appendRun(p,null,replacement,false);return;}replaceRange(p,field.start(),field.end(),replacement);return;}
        // Split only the blank run boundaries: fixed prefixes/units keep their original runs.
        splitAt(p,field.end());Node target=splitAt(p,field.start());
        if(target==null){appendRun(p,null,replacement,true);return;}
        int width=field.original().matches("[_＿]+")?field.original().length():0;
        int used=replacement.codePoints().map(c->c>255?2:1).sum();
        // Write once: clearing a split XMLBeans text node before rewriting it invalidates its CharNode links.
        replaceRange(p,field.start(),field.end(),replacement+" ".repeat(Math.max(0,width-used)));
        Node props=null;for(Node c=target.getFirstChild();c!=null;c=c.getNextSibling())if("rPr".equals(c.getLocalName()))props=c;
        if(props==null){props=p.getOwnerDocument().createElementNS(W,"w:rPr");target.insertBefore(props,target.getFirstChild());}
        boolean existing=false;for(Node c=props.getFirstChild();c!=null;c=c.getNextSibling())if("u".equals(c.getLocalName()))existing=true;
        if(!existing){Element u=p.getOwnerDocument().createElementNS(W,"w:u");u.setAttributeNS(W,"w:val","single");props.appendChild(u);}
    }
    private static Node splitAt(Node p,int at){
        List<Node> ts=new ArrayList<>();texts(p,ts);int position=0;
        for(Node t:ts){String value=value(t);int end=position+value.length();
            if(at>=position&&at<end){Node run=t.getParentNode();if(!"r".equals(run.getLocalName()))throw new DraftingException("填写位置包含不支持的嵌套结构，请重新核对字段",422);
                int offset=at-position;if(offset==0&&t==firstText(run))return run;
                Node after=run.cloneNode(false);for(Node c=run.getFirstChild();c!=null;c=c.getNextSibling())if("rPr".equals(c.getLocalName()))after.appendChild(c.cloneNode(true));
                Node tail=t.cloneNode(true);setText(t,value.substring(0,offset));setText(tail,value.substring(offset));after.appendChild(tail);
                for(Node c=t.getNextSibling();c!=null;){Node next=c.getNextSibling();after.appendChild(c);c=next;}
                run.getParentNode().insertBefore(after,run.getNextSibling());return after;
            }position=end;
        }return null;
    }
    private static Node firstText(Node run){for(Node c=run.getFirstChild();c!=null;c=c.getNextSibling())if("t".equals(c.getLocalName()))return c;return null;}
    private static void appendRun(Node p,Node prototype,String value,boolean underline){
        Node run=p.getOwnerDocument().createElementNS(W,"w:r");
        if(underline){Node props=p.getOwnerDocument().createElementNS(W,"w:rPr");Element u=p.getOwnerDocument().createElementNS(W,"w:u");u.setAttributeNS(W,"w:val","single");props.appendChild(u);run.appendChild(props);}
        Node t=p.getOwnerDocument().createElementNS(W,"w:t");run.appendChild(t);setText(t,value);p.appendChild(run);
    }
    private static List<Node> paragraphs(XWPFDocument doc){
        List<Node> result=new ArrayList<>();walk(doc.getDocument().getBody().getDomNode(),result);
        for(var header:doc.getHeaderList())for(var element:header.getBodyElements()){if(element instanceof XWPFParagraph p)walk(p.getCTP().getDomNode(),result);else if(element instanceof XWPFTable t)walk(t.getCTTbl().getDomNode(),result);}
        for(var footer:doc.getFooterList())for(var element:footer.getBodyElements()){if(element instanceof XWPFParagraph p)walk(p.getCTP().getDomNode(),result);else if(element instanceof XWPFTable t)walk(t.getCTTbl().getDomNode(),result);}
        return result;
    }
    private static void walk(Node node,List<Node> result){
        if(W.equals(node.getNamespaceURI())&&"p".equals(node.getLocalName()))result.add(node);
        for(Node c=node.getFirstChild();c!=null;c=c.getNextSibling())walk(c,result);
    }
    private static void texts(Node node,List<Node> result){
        for(Node c=node.getFirstChild();c!=null;c=c.getNextSibling())ownedTexts(c,result);
    }
    private static void ownedTexts(Node node,List<Node> result){
        if(W.equals(node.getNamespaceURI())&&"p".equals(node.getLocalName()))return;
        if(W.equals(node.getNamespaceURI())&&"t".equals(node.getLocalName()))result.add(node);
        else for(Node c=node.getFirstChild();c!=null;c=c.getNextSibling())ownedTexts(c,result);
    }
    private static String value(Node t){StringBuilder result=new StringBuilder();for(Node child=t.getFirstChild();child!=null;child=child.getNextSibling())if(child.getNodeValue()!=null)result.append(child.getNodeValue());return result.toString();}
    private static String text(Node p){List<Node> ts=new ArrayList<>();texts(p,ts);return ts.stream().map(TemplateLayout::value).reduce("",String::concat);}
    private static void replace(Node p,String value){
        List<Node> ts=new ArrayList<>();texts(p,ts);if(ts.isEmpty())throw new DraftingException("填写区域没有可替换文字",422);
        if(text(p).strip().matches("\\{\\{[^{}]+}}")&&value.contains("\n")&&!hasFixedObjects(p)){
            // A body placeholder expands into real paragraphs so first-line indentation repeats correctly.
            Node prototype=p.cloneNode(true),last=p;String[] lines=value.split("\\n",-1);replace(p,lines[0]);
            for(int i=1;i<lines.length;i++){Node next=prototype.cloneNode(true);p.getParentNode().insertBefore(next,last.getNextSibling());replace(next,lines[i]);last=next;}return;
        }
        String originalText=text(p);
        var blanks=TemplateFields.ranges(originalText);
        if(!blanks.isEmpty()){
            var values=TemplateFields.values(originalText,value);
            for(int i=blanks.size()-1;i>=0;i--)replaceRange(p,blanks.get(i)[0],blanks.get(i)[1],values.get(i));return;
        }
        var slots=java.util.regex.Pattern.compile("\\{\\{[^{}]+}}").matcher(originalText);
        List<int[]> ranges=new ArrayList<>();StringBuilder expression=new StringBuilder("(?s)^");int offset=0;
        while(slots.find()){expression.append(java.util.regex.Pattern.quote(originalText.substring(offset,slots.start()))).append("(.*?)");ranges.add(new int[]{slots.start(),slots.end()});offset=slots.end();}
        if(!ranges.isEmpty()){
            expression.append(java.util.regex.Pattern.quote(originalText.substring(offset))).append('$');var matched=java.util.regex.Pattern.compile(expression.toString()).matcher(value);
            if(!matched.matches())throw new DraftingException("填写内容改变了占位符之外的固定文字，请重新起草",422);
            for(int i=ranges.size()-1;i>=0;i--)replaceRange(p,ranges.get(i)[0],ranges.get(i)[1],matched.group(i+1));return;
        }
        // Keep each run and its formatting. Fixed prefixes/suffixes retain their original text nodes.
        String old=ts.stream().map(TemplateLayout::value).reduce("",String::concat);
        int prefix=0;while(prefix<old.length()&&prefix<value.length()&&old.charAt(prefix)==value.charAt(prefix))prefix++;
        int suffix=0;while(suffix<old.length()-prefix&&suffix<value.length()-prefix&&old.charAt(old.length()-1-suffix)==value.charAt(value.length()-1-suffix))suffix++;
        int position=0;boolean inserted=false;
        for(Node t:ts){String original=value(t);int end=position+original.length();StringBuilder replacement=new StringBuilder();
            if(position<prefix)replacement.append(original,0,Math.min(original.length(),prefix-position));
            if(!inserted&&(end>prefix||end==old.length())){replacement.append(value,prefix,value.length()-suffix);inserted=true;}
            int keepFrom=Math.max(0,old.length()-suffix-position);if(keepFrom<original.length())replacement.append(original.substring(keepFrom));
            setText(t,replacement.toString());position=end;
        }
    }
    private static boolean hasFixedObjects(Node node){
        if(W.equals(node.getNamespaceURI())&&Set.of("drawing","pict","object","fldChar","fldSimple","sectPr","bookmarkStart","bookmarkEnd","commentRangeStart","commentRangeEnd").contains(node.getLocalName()))return true;
        for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling())if(hasFixedObjects(child))return true;return false;
    }
    private static void replaceRange(Node p,int from,int to,String replacement){
        List<Node> nodes=new ArrayList<>();texts(p,nodes);int position=0;boolean inserted=false;
        if(from==to){
            for(Node t:nodes){String original=value(t);int end=position+original.length();if(from>=position&&from<=end){setText(t,original.substring(0,from-position)+replacement+original.substring(from-position));return;}position=end;}
        }
        for(Node t:nodes){String original=value(t);int end=position+original.length();
            if(end>from&&position<to){String before=position<from?original.substring(0,from-position):"",after=end>to?original.substring(to-position):"";setText(t,before+(inserted?"":replacement)+after);inserted=true;}
            position=end;
        }
    }
    private static void setText(Node t,String value){
        while(t.hasChildNodes())t.removeChild(t.getFirstChild());
        t.appendChild(t.getOwnerDocument().createTextNode(value));
        ((Element)t).setAttributeNS("http://www.w3.org/XML/1998/namespace","xml:space","preserve");
        // Word requires explicit breaks rather than literal newline characters in a w:t node.
        if(value.contains("\n")){
            String[] lines=value.split("\n",-1);while(t.hasChildNodes())t.removeChild(t.getFirstChild());t.appendChild(t.getOwnerDocument().createTextNode(lines[0]));Node last=t;
            for(int i=1;i<lines.length;i++){
                Node br=t.getOwnerDocument().createElementNS(W,"w:br");last.getParentNode().insertBefore(br,last.getNextSibling());
                Element next=t.getOwnerDocument().createElementNS(W,"w:t");next.setAttributeNS("http://www.w3.org/XML/1998/namespace","xml:space","preserve");next.appendChild(t.getOwnerDocument().createTextNode(lines[i]));br.getParentNode().insertBefore(next,br.getNextSibling());last=next;
            }
        }
    }
}
