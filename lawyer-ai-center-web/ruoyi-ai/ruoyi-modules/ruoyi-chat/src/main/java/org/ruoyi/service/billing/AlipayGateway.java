package org.ruoyi.service.billing;

import com.alipay.api.*;
import com.alipay.api.request.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Official SDK performs request signing and response verification. No client is created at startup. */
@Component @RequiredArgsConstructor
public class AlipayGateway {
    private final BillingProperties properties;
    private final ObjectMapper json;
    @jakarta.annotation.PostConstruct
    void disableSdkPayloadLogs(){
        com.alipay.api.internal.util.AlipayLogger.setNeedEnableLogger(false);
        com.alipay.api.internal.util.AlipayLogger.setJDKDebugEnabled(false);
    }
    public void requireEnabled() {
        if(!properties.isEnabled())throw new ServiceException("购买功能暂未开放",503);
        if(!properties.getAppId().matches("[0-9]{12,32}")||!properties.getSellerId().matches("[0-9]{12,32}"))throw new ServiceException("购买功能配置尚未完成",503);
        try {
            URI notify=URI.create(properties.getNotifyUrl());
            if(!"https".equals(notify.getScheme())||notify.getHost()==null||notify.getUserInfo()!=null||notify.getQuery()!=null)throw new IllegalArgumentException();
            if(!Files.isReadable(Path.of(properties.getPrivateKeyFile()))||!Files.isReadable(Path.of(properties.getAlipayPublicKeyFile())))throw new IllegalArgumentException();
        }catch(Exception e){throw new ServiceException("购买功能配置尚未完成",503);}
    }
    private String key(String file)throws Exception {
        String value=Files.readString(Path.of(file));
        if(value.length()>20000)throw new IllegalArgumentException();
        return value.replaceAll("-----[^-]+-----","").replaceAll("\\s","");
    }
    private AlipayClient client()throws Exception {
        requireEnabled();
        return new DefaultAlipayClient(properties.isSandbox()?"https://openapi-sandbox.dl.alipaydev.com/gateway.do":"https://openapi.alipay.com/gateway.do",
            properties.getAppId(),key(properties.getPrivateKeyFile()),"json","UTF-8",key(properties.getAlipayPublicKeyFile()),"RSA2");
    }
    public String precreate(String orderNo,String name,String amount,String expiresAt) {
        try {
            var request=new AlipayTradePrecreateRequest();request.setNotifyUrl(properties.getNotifyUrl());
            request.setBizContent(json.writeValueAsString(Map.of("out_trade_no",orderNo,"subject",name,"total_amount",amount,"time_expire",expiresAt,"seller_id",properties.getSellerId())));
            var response=client().execute(request);
            if(!response.isSuccess()||!orderNo.equals(response.getOutTradeNo())||response.getQrCode()==null)throw new ServiceException("暂时无法发起支付，请稍后从订单重试");
            URI qr=URI.create(response.getQrCode());
            if(!"https".equals(qr.getScheme())||qr.getHost()==null||!(qr.getHost().equals("qr.alipay.com")||properties.isSandbox()&&qr.getHost().endsWith(".alipaydev.com")))throw new ServiceException("支付二维码校验失败");
            return response.getQrCode();
        }catch(ServiceException e){throw e;}catch(Exception e){throw new ServiceException("支付服务暂时不可用，请稍后查询订单");}
    }
    public record Trade(String orderNo,String tradeNo,String status,String amount,String buyerPayTime) {}
    public Trade query(String orderNo) {
        try {
            var request=new AlipayTradeQueryRequest();request.setBizContent(json.writeValueAsString(Map.of("out_trade_no",orderNo)));
            var response=client().execute(request);
            if(!response.isSuccess()) {
                if("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode()))return new Trade(orderNo,"","WAIT_BUYER_PAY","",null);
                throw new ServiceException("订单状态暂时无法确认，请稍后重试");
            }
            if(!orderNo.equals(response.getOutTradeNo()))throw new ServiceException("支付订单校验失败");
            var format=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            return new Trade(orderNo,response.getTradeNo(),response.getTradeStatus(),response.getTotalAmount(),response.getSendPayDate()==null?null:format.format(response.getSendPayDate()));
        }catch(ServiceException e){throw e;}catch(Exception e){throw new ServiceException("订单状态暂时无法确认，请稍后重试");}
    }
    public boolean close(String orderNo) {
        try {
            var request=new AlipayTradeCloseRequest();request.setBizContent(json.writeValueAsString(Map.of("out_trade_no",orderNo)));
            var response=client().execute(request);return response.isSuccess()&&orderNo.equals(response.getOutTradeNo());
        }catch(Exception e){throw new ServiceException("支付取消结果尚未确认，请刷新订单状态");}
    }
    public boolean verify(Map<String,String> parameters) {
        requireEnabled();
        try {
            return "RSA2".equals(parameters.get("sign_type"))&&AlipaySignature.rsaCheckV1(parameters,key(properties.getAlipayPublicKeyFile()),"UTF-8","RSA2");
        }catch(Exception e){return false;}
    }
}
