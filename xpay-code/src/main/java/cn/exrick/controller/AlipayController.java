package cn.exrick.controller;

import cn.exrick.bean.Pay;
import cn.exrick.bean.dto.Result;
import cn.exrick.common.utils.EmailUtils;
import cn.exrick.common.utils.IpInfoUtils;
import cn.exrick.common.utils.ResultUtil;
import cn.exrick.common.utils.StringUtils;
import cn.exrick.service.PayService;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Exrickx
 */
@Controller
public class AlipayController {

    private static final Logger log= LoggerFactory.getLogger(AlipayController.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PayService payService;

    @Autowired
    private EmailUtils emailUtils;

    @Value("${ip.expire}")
    private Long IP_EXPIRE;

    @Value("${my.token}")
    private String MY_TOKEN;

    @Value("${email.sender}")
    private String EMAIL_SENDER;

    @Value("${email.receiver}")
    private String EMAIL_RECEIVER;

    @Value("${server.url}")
    private String SERVER_URL;

    @Value("${token.admin.expire}")
    private Long ADMIN_EXPIRE;

    private static final String CLOSE_DMF_KEY="XPAY_CLOSE_DMF_KEY";

    private static final String CLOSE_DMF_REASON="XPAY_CLOSE_DMF_REASON";

    /**
     * 你的私钥
     */
    private static final String privateKey = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCArjjVaWvz5E611PUdNv8AVCBriXUmOt8OVIu8qo/s3uU+3Zp5msCH6IjdCrtNTtXI592LaUKik9F9J2L2GAgIZBun0640ytbFr9EIhZV+Eo0HR03cQ0ebNaoKuQQCnacfinlNVulxmF7JK63gfoCt7sv6VBDaHpIt4rJ2XhOtb+rnSmaj5B4/EQnVQ3Op3SFctvzzxD8lRc1EkuhI1ulpxxPQB73WqI6c8eI1uU54PJUTQUKQLkhk8tnGaiIj/QaaSDcH/pbj4umL1qOsGHnIe2h468bWpKclcLNNJK7Rx9NvmBjbueSZZZFjYElLBvOjlwrAZnPyaLVR8nW5V+lHAgMBAAECggEAHQs1uLV6sCmvukq+qEFpfOGAD8WOs3pGLi3W/FZi9cu5Yl8SJrBPFF3fxkRN0j0g85+h2X2AlMFYXy8snu79oSc3NsIIZ9IAd84fGBVOkI4PVr05TaXB275ZCuKHjS+YMP5IMrSTLBiADFfXfvwOfzOOxomC5DhSNhkcvqqtNk/idqWVL0pvba8BsD8S9o3e3LgXTzhw2GCGT+DzVCGOgzG+MVrHX39Fs0tfCMscUgndOsIITSwquQa6boZrcTL448zjBrf276MA5Nn4NuDMQ/yRyALBcLO6RwxetrDhYYeODfj7tVoO1zv2GJYMoKU1tTyRYG0H3eZw8vhtBjH7gQKBgQC6jOQM57giCgNg6S3s6+e/1QzMX+nHPf6ybl8bL+2ZweSp14Vopd8MOR54IiOECgiqi4AYJ9Yyf1F98LEkWBb1anNEaszj3+VJlpmamyIeGlMLMaCRyqGPunrChhetiqZ/vyRIFwadAPO3ZmLjSf+ktK3QS6FhXNDoWE9He3ROyQKBgQCwlhKFIADPWKm1HnfFx39TOXT0OU8u7KdaPccyxcFAnuFrt47H7cyakyiF1WHrtWYv9C0bMQYff7Qg5rzVg2BscuG0qb3awjEeyJtfckcFnjiaHl8ejsLMMI6OAeaDhUeMFR/iPgYj/ZsEsQu1FVlwiPhX/6p8CPQrPMNwuSEvjwKBgBTScwXpu0i5W2UuwbyHHEfyLMqHDh36EnqyDRWIxPl7hd1bO/3RPdMscPUxwksTn6IBjhukHqlmQp9PBUasOmvMJO+HCDWLIRmUoLJ8DFPV1l0Sffyn5F6Zjug1dWEeb7UkjZUqVMejepCG8hSyhsFIlXoBZxLp/Dti1/5/jhzZAoGAReei/UBmEa8vv5uIHquTJAci3WuyhQj6VycNrHPMxSAgbNwFke/5h1eqOxD16rGs+l7XXGRT4DRVwpCVQJD5ovT3lOV9WxR1DZKsr2Q16WtxTNGpJhet1deDF4R5FKe2YyYZsR6Mn8LNk1XjJJSHC52tcmirvN5uio9Lb1xaXtECgYBMlfONKijQ92zuxMXkzEAj7HAxwgujt8a4sWfZ8J6Vrt43O3eNLHGoKDFzr3QK7SL66fCoO5iE6owpq9TPVYpNZd6AKMSLut0RuXjBIwql/CLnMYVlsyOivk/dvSndVdRq1tybcyiDHSF0A4XQ43X5ZfMnfbmoAQ+PYjrJVVThGw==";

    /**
     * 你的公钥
     */
    private static final String publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAstEkedkPUW3UCiBKoQm7GoqLUyIYH6Aql4Age9ytmLYRnN/Rir4Q50Z9WgmNlCgcRQbb30AqKGE+93T9udeq5C0VNcv2NbXncv6+Jn++KzQgg0jpeHggWu76fh60Q8ziHOeajQr+KQXYgV65m00lqxKEQil/r6EPu3mJpay9FLcSnSgiEngu6Bqz7kOCas6NauGjBDW+MWsXPlBORJVzBrjtOX6b/RtYWqO9md9QSbZFEt/bxOleCpYiLyV9TdBNqdBEJBgI7iNz2G4tkxRPIm2UcZqRybtTk69AuyKNxxqOTa+8S5P/PvEn6yMrvMK3QGRKL5XukCmrnSDiXY+t9wIDAQAB";

    /**
     * 你的应用ID
     */
    private static final String appId = "2021001156676003";

    /**
     * 回调通知接口链接
     */
    private static final String notifyUrl = "http://msl1214.iok.la/alipay/notify";

    /**
     * 生成二维码
     * @param pay
     * @param request
     * @return
     * @throws AlipayApiException
     */
    @RequestMapping(value = "/alipay/precreate",method = RequestMethod.POST)
    @ResponseBody
    public Result<Object> getPayState(@ModelAttribute Pay pay, HttpServletRequest request) throws AlipayApiException {

        if(pay.getMoney()==null || !EmailUtils.checkEmail(pay.getEmail())){
            return new ResultUtil<Object>().setErrorMsg("请填写正确的通知邮箱或金额");
        } else if (pay.getMoney().compareTo(new BigDecimal("1000.00"))==1) {
            return new ResultUtil<Object>().setErrorMsg("当面付单笔金额不得大于1000");
        }

        String isOpenDMF = redisTemplate.opsForValue().get(CLOSE_DMF_KEY);
        String dmfReason = redisTemplate.opsForValue().get(CLOSE_DMF_REASON);
        String msg = "";
        if(StringUtils.isNotBlank(isOpenDMF)){
            msg = dmfReason + "如有疑问请进行反馈";
            return new ResultUtil<Object>().setErrorMsg(msg);
        }
        //防炸库验证
        String ip = IpInfoUtils.getIpAddr(request);
        if("0:0:0:0:0:0:0:1".equals(ip)){
            ip = "127.0.0.1";
        }
        ip="DMF:"+ip;
        String temp = redisTemplate.opsForValue().get(ip);
        Long expire = redisTemplate.getExpire(ip, TimeUnit.SECONDS);
        if(StringUtils.isNotBlank(temp)){
            return new ResultUtil<Object>().setErrorMsg("您提交的太频繁啦，作者的学生服务器要炸啦！请"+expire+"秒后再试");
        }
        payService.addPay(pay);
        //记录缓存
        redisTemplate.opsForValue().set(ip,"added", 1L, TimeUnit.MINUTES);

        AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do",
                appId,privateKey,"json","GBK",publicKey,"RSA2");
        AlipayTradePrecreateRequest r = new AlipayTradePrecreateRequest();
        r.setBizContent("{" +
                "\"out_trade_no\":\""+pay.getId()+"\"," +
                "\"total_amount\":"+pay.getMoney()+"," +
                "\"subject\":\"XPay-向作者Exrick捐赠\"" +
                "  }");
        // 设置通知回调链接
        r.setNotifyUrl(notifyUrl);
        AlipayTradePrecreateResponse response = alipayClient.execute(r);
        if(!response.isSuccess()){
            log.error(response.getBody());
            return new ResultUtil<Object>().setErrorMsg("调用支付宝接口生成二维码失败，请向作者反馈");
        }
        Map<String, Object> result = new HashMap<>(16);
        result.put("id", pay.getId());
        result.put("qrCode", response.getQrCode());
        return new ResultUtil<Object>().setData(result);
    }

    /**
     * 查询支付结果
     * @param out_trade_no
     * @return
     * @throws AlipayApiException
     */
    @RequestMapping(value = "/alipay/query/{out_trade_no}",method = RequestMethod.GET)
    @ResponseBody
    public Result<Object> queryPayState(@PathVariable String out_trade_no) throws AlipayApiException {

        AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do",
                appId,privateKey,"json","GBK",publicKey,"RSA2");
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{" +
                "\"out_trade_no\":\""+out_trade_no+"\"" +
                "  }");
        AlipayTradeQueryResponse response = alipayClient.execute(request);
        if(response!=null&&response.isSuccess()&&"TRADE_SUCCESS".equals(response.getTradeStatus())){
            sendActiveEmail(out_trade_no);
            return new ResultUtil<Object>().setData(1);
        }else{
            return new ResultUtil<Object>().setData(0);
        }
    }

    /**
     * 支付宝通知回调
     * @return
     */
    @RequestMapping(value = "/alipay/notify")
    @ResponseBody
    public String notify(@RequestParam(required = false) String out_trade_no,
                         @RequestParam(required = false) String trade_status) {
        
        if("TRADE_SUCCESS".equals(trade_status)){
            sendActiveEmail(out_trade_no);
        }
        return "success";
    }

    @Async
    public void sendActiveEmail(String id){

        Pay pay = payService.getPay(id);
        if(pay.getState()==1){
            return;
        }
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("dmf:"+pay.getId(), token, ADMIN_EXPIRE, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(pay.getId(), token, ADMIN_EXPIRE, TimeUnit.DAYS);
        pay = getAdminUrl(pay, pay.getId(), token, MY_TOKEN);

        if(pay.getMoney().compareTo(new BigDecimal("0.99"))==1){
            emailUtils.sendTemplateMail(EMAIL_SENDER,EMAIL_RECEIVER,"【XPay支付系统】当面付收款"+pay.getMoney()+"元","email-admin",pay);
        }
        if(pay.getMoney().compareTo(new BigDecimal("9.99"))==1&&pay.getMoney().compareTo(new BigDecimal("68.00"))==-1){
            // 发送xpay
            emailUtils.sendTemplateMail(EMAIL_SENDER, pay.getEmail(),"【XPay个人收款支付系统】支付成功通知（附下载链接）","pay-success", pay);
        }else if(pay.getMoney().compareTo(new BigDecimal("198.00"))==0||pay.getMoney().compareTo(new BigDecimal("198.00"))==1){
            // 发送xboot
            emailUtils.sendTemplateMail(EMAIL_SENDER, pay.getEmail(),"【XPay个人收款支付系统】支付成功通知（附下载链接）","sendxboot", pay);
        }
        pay.setState(1);
        payService.updatePay(pay);
        redisTemplate.delete("xpay:"+pay.getId());
    }

    /**
     * 拼接管理员链接
     */
    public Pay getAdminUrl(Pay pay,String id,String token,String myToken){

        String pass=SERVER_URL+"/pay/pass?sendType=0&id="+id+"&token="+token+"&myToken="+myToken;
        pay.setPassUrl(pass);

        String pass2=SERVER_URL+"/pay/pass?sendType=1&id="+id+"&token="+token+"&myToken="+myToken;
        pay.setPassUrl2(pass2);

        String pass3=SERVER_URL+"/pay/pass?sendType=2&id="+id+"&token="+token+"&myToken="+myToken;
        pay.setPassUrl3(pass3);

        String back=SERVER_URL+"/pay/back?id="+id+"&token="+token+"&myToken="+myToken;
        pay.setBackUrl(back);

        String passNotShow=SERVER_URL+"/pay/passNotShow?id="+id+"&token="+token;
        pay.setPassNotShowUrl(passNotShow);

        String edit=SERVER_URL+"/pay-edit?id="+id+"&token="+token;
        pay.setEditUrl(edit);

        String del=SERVER_URL+"/pay-del?id="+id+"&token="+token;
        pay.setDelUrl(del);

        String close=SERVER_URL+"/pay-close?id="+id+"&token="+token;
        pay.setCloseUrl(close);

        String statistic=SERVER_URL+"/statistic?myToken="+myToken;
        pay.setStatistic(statistic);

        return pay;
    }
}
