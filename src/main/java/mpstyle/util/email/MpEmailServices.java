/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util.email;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;


/**
 *
 * @author equake58
 */
public class MpEmailServices {
    
    public static final String module = MpEmailServices.class.getName();
    
    /**
     * 
     * @param attachFileSourcePath - File path of the resource to attach
     * @param attchFilename - File name for the attachment
     * @param sendFrom
     * @param sendTo
     * @param sendCc list of addresses separated by a comma (,)
     * @param sendBcc
     * @param subject 
     * @param attachMimeType - Attachment MIME Type (depends on the attachment file extension/type)
     * https://tools.ietf.org/html/rfc1521 - https://www.mario-online.com/mime_types.html
     * @param username
     * @param password
     * @param dispatcher
     * @return 
     */
    public static boolean sendMailWithAttachment(String attachFileSourcePath, String attchFilename, String sendFrom, String sendTo, 
            String sendCc, String sendBcc, String subject, String attachMimeType, String username, String password, LocalDispatcher dispatcher) {
        
        boolean emailSent = true;
        
        Map<String, Object> emailContext = new HashMap<>();

        DataHandler dh = null;

        DataSource source = new FileDataSource(attachFileSourcePath);
        dh = new DataHandler(source);
        
        List<Map<String, Object>> bodyParts = UtilMisc.toList(UtilMisc.toMap("content", dh, "type", attachMimeType, "filename", attchFilename));
        emailContext.put("login.username", username);
        emailContext.put("login.password", password);
        emailContext.put("sendFrom", sendFrom);
        emailContext.put("sendTo", sendTo);
        if(UtilValidate.isNotEmpty(sendCc)) {
            emailContext.put("sendCc", sendCc);
        }
        if(UtilValidate.isNotEmpty(sendBcc)) {
            emailContext.put("sendBcc", sendBcc);
        }
        emailContext.put("subject", subject);
        emailContext.put("bodyParts", bodyParts);
        
        Map<String, Object> result = null;
        
        try {
            result = dispatcher.runSync("sendMailMultiPart", emailContext);
        }catch(GenericServiceException gse) {
            Debug.logError(gse.getMessage(), module);
        }
        
        if(ServiceUtil.isSuccess(result)) {
            Debug.logWarning("Mail sent with success", module);
        }else{
            Debug.logError("Error in sending mail", module);
            emailSent = false;
        }
        
        return emailSent;
        
    }
    
    
    public static boolean sendSimpleMail(String body, String sendFrom, String sendTo, 
            String sendCc, String sendBcc, String subject, String contentType, String username, String password, LocalDispatcher dispatcher) {
        
        boolean emailSent = true;
        
        Map<String, Object> emailContext = new HashMap<>();
        
        emailContext.put("login.username", username);
        emailContext.put("login.password", password);
        emailContext.put("sendFrom", sendFrom);
        emailContext.put("sendTo", sendTo);
        if(UtilValidate.isNotEmpty(sendCc)) {
            emailContext.put("sendCc", sendCc);
        }
        if(UtilValidate.isNotEmpty(sendBcc)) {
            emailContext.put("sendBcc", sendBcc);
        }
        emailContext.put("subject", subject);
        emailContext.put("contentType", contentType);
        emailContext.put("body", body);
        
        Map<String, Object> result = null;
        
        try {
            result = dispatcher.runSync("sendMail", emailContext);
        }catch(GenericServiceException gse) {
            Debug.logError(gse.getMessage(), module);
        }
        
        if(ServiceUtil.isSuccess(result)) {
            Debug.logWarning("Mail sent with success", module);
        }else{
            Debug.logError("Error in sending mail", module);
            emailSent = false;
        }
        
        return emailSent;
        
    }
    
} //end class
