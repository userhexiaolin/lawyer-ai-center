package org.ruoyi.service.legal;
import java.util.*;
/** Initial built-in types only. Runtime options and validation use DocTemplateTypeService. */
public enum DocumentType {
 COMPLAINT("民事起诉状"),DEFENSE("民事答辩状"),LETTER("律师函"),CONTRACT("合同文书"),OPINION("法律意见书"),GENERAL("自由描述文书");
 private final String label;
 DocumentType(String label){this.label=label;}
 public record Option(String value,String label){}
 public static List<Option> options(){return Arrays.stream(values()).map(t->new Option(t.label,t.label)).toList();}
}
