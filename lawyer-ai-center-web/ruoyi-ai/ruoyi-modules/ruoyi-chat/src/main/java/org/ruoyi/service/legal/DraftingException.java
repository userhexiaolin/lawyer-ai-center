package org.ruoyi.service.legal;
/** Only application-authored, user-readable configuration/validation errors. */
public class DraftingException extends RuntimeException {
    private final int code;
    public DraftingException(String message,int code){super(message);this.code=code;}
    public int getCode(){return code;}
}
