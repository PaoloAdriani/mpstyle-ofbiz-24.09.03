package main.java.mpstyle.shipment.verify;

import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.shipment.verify.VerifyPickSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * @author equake58
 */
public class MpVerifyPickServices {
    
    public static final String module = MpVerifyPickServices.class.getName();
    public static final String mp_shipment_resource = "mpshipment.properties";
    
    public Map<String, Object> verifySingleItemByGoodIdentification(DispatchContext dctx, Map<String, Object> context) {
        
        Locale locale = (Locale) context.get("locale");
        Delegator delegator = dctx.getDelegator();
        VerifyPickSession pickSession = (VerifyPickSession) context.get("verifyPickSession");
        String orderId = (String) context.get("orderId");
        String shipGroupSeqId = (String) context.get("shipGroupSeqId");
        String idValue = (String) context.get("idValue"); //id of the GoodiIdentification
        String originGeoId = (String) context.get("originGeoId");
        BigDecimal quantity = (BigDecimal) context.get("quantity");
        
        GenericValue goodIdentificationGV = null;
        
        String productId = null;
        
        String goodIdentificationUnique = UtilProperties.getPropertyValue(mp_shipment_resource, "shipment.verify.goodidentification.unique");
        
        if(goodIdentificationUnique == null) {
            goodIdentificationUnique = "Y";
        }
        
        //Search for the Good Identification Number (idValue) and it's related product
        EntityCondition cond = EntityCondition.makeCondition("idValue", EntityOperator.EQUALS, idValue);
        
        List<GenericValue> goodIdentificationList = null;
        
        try {
            goodIdentificationList = delegator.findList("GoodIdentification", cond, null, UtilMisc.toList("lastUpdatedStamp"), null, false);
        } catch (GenericEntityException ex) {
            Debug.logError(ex.getMessage(), module);
            return ServiceUtil.returnError(ex.getMessage());
        }
        
        if(UtilValidate.isEmpty(goodIdentificationList)) {
            Debug.logError("No GoodIdentification number found for ["+idValue+"].", module);
            return ServiceUtil.returnError("No GoodIdentification number found for ["+idValue+"].");
        }
        
        if(goodIdentificationList.size() == 1) {
            
            goodIdentificationGV = EntityUtil.getFirst(goodIdentificationList);
            
            productId = (String) goodIdentificationGV.get("productId");
   
        }else{
            
            //more than one record returned
            if("Y".equals(goodIdentificationUnique)) { //consider the good identification as unique so select the last updated record
                
                goodIdentificationGV = EntityUtil.getFirst(goodIdentificationList);
            
                productId = (String) goodIdentificationGV.get("productId");
                
                
            }else{
                /*
                * TODO: more than one record found and the a choice on
                * which code select must be implemented.
                */
                String msg = "WARNING: More than one record returned for GoodIdentification with idValue ["+idValue+"], but no choice method is implemented. Cannot verify item.";
                Debug.logWarning(msg, module);
                return ServiceUtil.returnError(msg);
            }
            
        }
        

        if (quantity != null) {
            try {
                pickSession.createRow(orderId, null, shipGroupSeqId, productId, originGeoId, quantity, locale);
            } catch (GeneralException e) {
                return ServiceUtil.returnError(e.getMessage());
            }
        }
        
        
        return ServiceUtil.returnSuccess();
        
    };
    
    
} //end class
