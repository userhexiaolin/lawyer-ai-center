package org.ruoyi.service.lawyer;
/** Safe, enumerated operational error; never wraps original document/parser exception text. */
public class DossierReadException extends RuntimeException {
    private final String errorCode;
    public DossierReadException(String errorCode,String message){super(message);this.errorCode=errorCode;}
    public String errorCode(){return errorCode;}
}
