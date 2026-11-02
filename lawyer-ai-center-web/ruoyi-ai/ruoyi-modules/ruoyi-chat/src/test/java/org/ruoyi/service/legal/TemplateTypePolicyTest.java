package org.ruoyi.service.legal;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TemplateTypePolicyTest {
 @Test void titleClassificationIsSpecificAndDoesNotUseBodyReferences(){
  var allowed=List.of("律师函","合同文书","借款合同","民事起诉状");
  assertEquals("律师函",TemplateTypePolicy.title("# 律师函（模板）\n依据借款合同",allowed).documentType());
  assertEquals("借款合同",TemplateTypePolicy.title("个人借款合同\n以下引用律师函",allowed).documentType());
  assertEquals("民事起诉状",TemplateTypePolicy.title("民事起诉状（借款合同纠纷）\n诉讼请求",allowed).documentType());
  assertNull(TemplateTypePolicy.title("律师函及合同文书\n合成材料",allowed).documentType());
  assertNull(TemplateTypePolicy.title("模板说明\n请遵守借款合同约定，收到律师函后履行义务。",allowed));
 }
 @Test void evidenceMustBeInTheProvidedExcerptAndCannotBeAnInventedRationale(){
  var bad=new TemplateTypeDecisionStore.Decision("律师函","AI","正文中不存在的理由");
  assertFalse(TemplateTypePolicy.valid(bad,List.of("律师函"),"合成正文"));
  var good=new TemplateTypeDecisionStore.Decision("律师函","AI","合成正文");
  assertTrue(TemplateTypePolicy.valid(good,List.of("律师函"),"合成正文"));
  assertFalse(TemplateTypePolicy.valid(good,List.of("合同文书"),"合成正文"));
 }
}
