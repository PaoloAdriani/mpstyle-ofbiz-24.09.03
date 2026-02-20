/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.ws;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import mpstyle.log.MpStyleLogger;
import mpstyle.util.MpStyleShipmentUtil;
import mpstyle.util.MpStyleUtil;
import mpstyle.util.email.MpEmailServices;
import mpstyle.util.email.MpEmailUtil;
import mpstyle.util.http.MpHttpUtil;
import mpstyle.util.product.MpProductUtil;
import mpstyle.ws.availability.InventoryItems;
import org.apache.cxf.jaxrs.client.WebClient;

import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.order.shoppingcart.CartItemModifyException;
import org.apache.ofbiz.order.shoppingcart.ShoppingCart;
import org.apache.ofbiz.order.shoppingcart.ShoppingCartEvents;
import org.apache.ofbiz.order.shoppingcart.ShoppingCartItem;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 *
 * @author equake58
 */
public class MpStyleWSEvents {
    
    public static final String MODULE = MpStyleWSEvents.class.getName();
    public static final String RESOURCE_ERROR = "MpStyleWSErrorUiLabels";
    private static final String OMNI_SYSTEM_RESOURCE_ID = "mpomni";

    /**
     * Wrapper for checking MpStyle availability process.
     * @param request
     * @param response
     * @return 
     */
    public static String checkMpAvailability(HttpServletRequest request, HttpServletResponse response) {
        
        final String method = "checkMpAvailability";
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        ShoppingCart cart = ShoppingCartEvents.getCartObject(request);
        Locale locale = UtilHttp.getLocale(request);
        String result = "error";
        boolean test = EntityUtilProperties.getPropertyAsBoolean(OMNI_SYSTEM_RESOURCE_ID, "wsavail.environment", false);
        boolean wsEmailNotifyEnabled = false;
        boolean useCustomLogger = false; 
        boolean cartModified = false;
        
        Map<String, Object> orderItemAvailabilityMap = null;
        
        /*
        Map used to relate cart item sequence id (calculated using cart item index)
        to a specific cart object.
        Required during removal of cart itmes since indexes will change during removal.
        */
        Map<String, Object> orderItemCartObjectMap = null;
        
        
        String productStoreId = cart.getProductStoreId();
        
        String logfilename = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsavail.logfilename", delegator);
        String logdirpath = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsavail.logdirpath", delegator);
        String wsurl = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsavail.url", delegator);
        String wspath = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsavail.path", delegator);
        String wsSeasons = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "mpWsSeasons", delegator);
        String wsFacilities = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "mpWsFacilities", delegator);
        String username = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "serviceUsername", delegator);
        String password = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "servicePassword", delegator);
        String wsMailNotify = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "ws.mail.notify", delegator);
        if(wsMailNotify != null) {
            wsEmailNotifyEnabled = "Y".equals(wsMailNotify);
        }
        
        String wsMailFromAddress = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "ws.mail.fromAddress", delegator);
        String wsMailToAddress = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "ws.mail.toAddress", delegator);
        String wsMailCcAddress = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "ws.mail.ccAddress", delegator);
        
        //Creation of the custom logger file
        MpStyleLogger logger = null;
        
        if(logfilename == null  || UtilValidate.isEmpty(logfilename.trim()) || logdirpath == null || UtilValidate.isEmpty(logdirpath.trim())) {
            Debug.logWarning("Missing system properties [mpomni/wsavail.logfilename] and [mpomni/wsavail.logdirpath]. Cannot use custom logger file. Using standard only.", MODULE);
            useCustomLogger = false;
        }else{
            logger = new MpStyleLogger(delegator.getDelegatorTenantId(), logfilename.trim(), logdirpath.trim());
            useCustomLogger = true;
        }
        
        if(logger == null) {
            useCustomLogger = false;
            Debug.logWarning("*** Custom logger is null. Using standard.", MODULE);
        }
        
        if(useCustomLogger) logger.logInfo("******** START (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
        
        /* ##### Perform check on system properties ##### */
        
        /* if service username and password are not set, then we cannot call services due to 
           auth problems. So do not perform the call and proceed anyway.
        */
        if(username == null || UtilValidate.isEmpty(username.trim()) || password == null || UtilValidate.isEmpty(password.trim())) {
            String msg = "*** Service username and/or password not set as SystemProperty [mpomni/serviceUsername] and [mpomni/servicePassword]. Could not call services with authorization. Do not perform ws call.";
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError(msg, MODULE);
            return "success";
        }
        
        username = username.trim();
        password = password.trim();
        
        /* checking email parameters */
        if(wsMailFromAddress == null || UtilValidate.isEmpty(wsMailFromAddress.trim()) || wsMailToAddress == null || UtilValidate.isEmpty(wsMailToAddress.trim())) {
            wsEmailNotifyEnabled = false;
            
            String msg = "Missing one or both email system property [mpomni/ws.mail.fromAddress], [mpomni/ws.mail.toAddress]. Disable email notify.";
            
            if(useCustomLogger) logger.logWarning(msg);
            Debug.logWarning(msg,MODULE);
                    
        }else{
        
            wsMailFromAddress = wsMailFromAddress.trim();
            wsMailToAddress = wsMailToAddress.trim();
        }
        
        if(!wsEmailNotifyEnabled) {
            String msg = "Email notify system disabled.";
            if(useCustomLogger) logger.logWarning(msg);
            Debug.logWarning(msg, MODULE);
        }
        
        /*
        If no seasons are enabled for use with ws, return success and do nothing.
        */
        if(wsSeasons == null || UtilValidate.isEmpty(wsSeasons.trim())) {
            
            String msg = "Seasons not enabled for ws. Check SystemProperty [mpomni/mpWsSeasons]. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            return "success";
        }
        
        wsSeasons = wsSeasons.trim();
        
        //split seasons using commas
        String []seasonArray = wsSeasons.split(",");
        
        if(seasonArray.length <= 0) {
            String msg = "Splitted seasons array is empty. Check the SystemProperty [mpomni/mpWsSeasons]; put in values separated by commas. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            return "success";
        }
        
        ArrayList<String> enabledSeasons = new ArrayList(Arrays.asList(seasonArray));
        
        /*
        If no facilities are enabled for use with ws, return success and do nothing.
        */
        if(wsFacilities == null || UtilValidate.isEmpty(wsFacilities.trim())) {
            
            String msg = "No facilities enabled for ws. Check SystemProperty [mpomni/mpWsFacilities]. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            return "success";
        }
        
        wsFacilities = wsFacilities.trim();
        
        //split seasons using commas
        String []wsFacilityArray = wsFacilities.split(",");
        
        if(wsFacilityArray.length <= 0) {
            String msg = "Splitted facilities array is empty. Check the SystemProperty [mpomni/mpWsFacilities]; put in values separated by commas. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            return "success";
        }
        
        ArrayList<String> enabledWsFacilities = new ArrayList(Arrays.asList(wsFacilityArray));
        
        /* If ws url is not set cannot perform the call. */
        if(wsurl == null || UtilValidate.isEmpty(wsurl.trim())) {
            
            String msg = "*** WebService URL for inventory availabilty is not set as SystemProperty [mpomni/wsavail.url]. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            
            if(wsEmailNotifyEnabled) {
                
                StringBuilder emailBody = new StringBuilder();
                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - MpCommerce WS Availability"));
                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));

                String emailSubject = "ERROR - WS " + wspath;

                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
                
            }
            
            return "success";
        }
        
        wsurl = wsurl.trim();
        
        /* If ws path is not set cannot perform the call. */
        if(wspath == null || UtilValidate.isEmpty(wspath.trim())) {
            
            String msg = "*** WebService Path for inventory availabilty is not set as SystemProperty [mpomni/wsavail.path]. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            
            if(wsEmailNotifyEnabled) {
                
                StringBuilder emailBody = new StringBuilder();
                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - MpCommerce WS Availability"));
                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));

                String emailSubject = "ERROR - WS " + wspath;

                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
                
            }
            
            return "success";
        }
        
        wspath = wspath.trim();
        
        List<GenericValue> storeAllFacilityList = MpStyleShipmentUtil.getProductStoreFacilities(productStoreId, delegator);
        
        if(storeAllFacilityList == null) {
            if(useCustomLogger) {
                logger.logError("Facility List for productStore [" + productStoreId + "] is null. Something strange happened here. Trying to continue with the order anyway");
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError("Facility List for productStore [" + productStoreId + "] is null. Something strange happened here. Trying to continue with the order anyway", MODULE);
            return "success";
        }
        
        //Filter out ws facilities from store facilities to separate ws and non-ws facilities
        List<String> otherStoreFacilities = new ArrayList<>();
        
        for(GenericValue storeFacility : storeAllFacilityList) {
            String _storeFacilityId = (String) storeFacility.get("facilityId");
            if(!enabledWsFacilities.contains(_storeFacilityId) && !otherStoreFacilities.contains(_storeFacilityId)) {
                otherStoreFacilities.add(_storeFacilityId);
            }
        }
        
        if(useCustomLogger) {
            logger.logInfo("WS Store Facilities => "+enabledWsFacilities);
            logger.logInfo("Other Store Facilities => "+otherStoreFacilities);
        }
        
        //Building request object on all cart items
        Debug.logWarning("Building InventoryItems request object", MODULE);
        List<ShoppingCartItem> cartItems = cart.items();
        orderItemCartObjectMap = new HashMap<>();
        
        if(!test) {
        
        InventoryItems inventoryItemRequestObj = new InventoryItems();
        
        for(ShoppingCartItem cartItem : cartItems) {
            
            String productId = cartItem.getProductId();
            
            String productSeason = MpProductUtil.getProductSeason(productId, delegator);
            
            if(productSeason == null) {
                String msg = "Product Season is null for productId ["+productId+"]. Skip this product.";
                if(useCustomLogger) logger.logWarning(msg);
                Debug.logWarning(msg, MODULE);
                continue;
            }
            
            //check if this product is of an enabled season: if not skip it
            if(!enabledSeasons.contains(productSeason)) {
                String msg = "Product season ["+productSeason+"] of product [" + productId + "] is not enabled for ws. Skip this product.";
                if(useCustomLogger) logger.logWarning(msg);
                Debug.logWarning(msg, MODULE);
                continue;
            }
            
            //index of a list starting from 0: add 1 to correctly convert into and order item seq id
            int itemIndex = cart.getItemIndex(cartItem) + 1;
            String orderItemSeqId = UtilFormatOut.formatPaddedNumber(itemIndex, 5);
            
            orderItemCartObjectMap.put(orderItemSeqId, cartItem);
            
            Debug.logWarning("Creating object for cart item: product: "+productId+" - item seq id: "+orderItemSeqId, MODULE);
        
            InventoryItems.InventoryItem ii = new InventoryItems.InventoryItem();
            
            ii.setSku(productId);
            ii.setOrderId("");
            ii.setOrderItemSeqId(orderItemSeqId);
            
            InventoryItems.InventoryItem.Facilities iifacilities = new InventoryItems.InventoryItem.Facilities();
            
            ii.setFacilities(iifacilities);
            
            //create a facility element for each Facility assoc to the store
            for(String _facilityId : enabledWsFacilities) {
            
                InventoryItems.InventoryItem.Facilities.Facility ii_facility = new InventoryItems.InventoryItem.Facilities.Facility();
                
                Map<String, Object> invAvailByFac = new HashMap<>();
                BigDecimal invProductFacATP = BigDecimal.ZERO;
                
                boolean beginTransaction = false;
                
                try {
                    if (TransactionUtil.getStatus() == TransactionUtil.STATUS_NO_TRANSACTION) {
                        beginTransaction = TransactionUtil.begin();
                    }
                
                    try {

                        invAvailByFac.put("productId", productId);
                        invAvailByFac.put("facilityId", _facilityId);
                        invAvailByFac.put("login.username", username);
                        invAvailByFac.put("login.password", password);

                        Map<String, Object> invAvailFacResultMap = dispatcher.runSync("getInventoryAvailableByFacility", invAvailByFac);

                        if(invAvailFacResultMap != null && ServiceUtil.isSuccess(invAvailFacResultMap)) {
                            invProductFacATP = (BigDecimal) invAvailFacResultMap.get("availableToPromiseTotal");
                        }

                    }catch(GenericServiceException gse) {
                        String msg = "Error in running service getInventoryAvailableByFacility for product [" + productId + "] and facility [" + _facilityId + "]. Error is => " + gse.getMessage();
                        if(useCustomLogger) logger.logError(msg);
                        Debug.logError(msg, MODULE);
                    }
                
                } catch (GenericTransactionException ex) {
                    try {
                        TransactionUtil.rollback(beginTransaction, "Error occured during transaction. Rolling back.", ex);
                    } catch (GenericTransactionException ex1) {
                        Debug.logError("Error in rolling back transaction. Error is => " + ex1.getMessage(), MODULE);
                    }
                }finally {
                    try {
                        TransactionUtil.commit(beginTransaction);
                    } catch (GenericTransactionException ex) {
                        Debug.logError("Error in committing transaction. Error is => " + ex.getMessage(), MODULE);
                    }
                }

                //These two fields are filled up by the producer erp service, so set them to some default/empty value
                ii_facility.setId(_facilityId);
                ii_facility.setAtp(invProductFacATP);
                
                ii.getFacilities().getFacility().add(ii_facility);
                
            }
            
            inventoryItemRequestObj.getInventoryItem().add(ii);
       
        }
        
        //Check if <inventory_item> objects has been created. If not, do not perform the ws call
        if(inventoryItemRequestObj.getInventoryItem() != null && inventoryItemRequestObj.getInventoryItem().size() <= 0) {
            String msg = "InventoryItems request object does not contain any object. Are all the product seasons of the cart item enabled? Not doing the call.";
            if(useCustomLogger) logger.logWarning(msg);
            Debug.logWarning(msg, MODULE);
            return "success";
        }
        
        
        //Marshall Java Object to XML for the ws call
        String xmlContent = null;
        
        try
        {
            //Create JAXB Context
            JAXBContext jaxbContext = JAXBContext.newInstance(InventoryItems.class);
             
            //Create Marshaller
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            
            //Add  pretty print formatting for xml
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            //Print XML String to Console
            StringWriter sw = new StringWriter();
             
            //Write XML to StringWriter
            jaxbMarshaller.marshal(inventoryItemRequestObj, sw);
             
            //Verify XML Content
            xmlContent = sw.toString();
            
            if(useCustomLogger) logger.logInfo("Request Inventory Object => "+xmlContent);
            Debug.logWarning("### Marshalled InventoryItems Object => "+ xmlContent, MODULE);
 
        } catch (JAXBException e) {
            String msg = "Error in JAXB Marshalling of entity InventoryItems into XML. Cannot call WS. ["+e.getMessage()+"].";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            
            return "success";
        }
        
        /* testing the wsurl: check if is valid and available */
        if(!MpHttpUtil.checkWebResourceURI(wsurl)) {
            String msg = "*** Problems in testing connection for URL ["+ wsurl + "]. Check if URL exists or you are connected to Internet. Do not call ws. ***";
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError(msg, MODULE);
            
            if(wsEmailNotifyEnabled) {
                StringBuilder emailBody = new StringBuilder();
                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - WS MpCommerce WS Availability"));
                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));

                String emailSubject = "ERROR - WS " + wspath;

                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);

            }
            
            return "success";
            
        } 
       
        
        /* ##### REST REQUEST PHASE ##### */
        if(useCustomLogger) logger.logInfo("Sending ws request to URL [" + wsurl + "] and path [" + wspath + "]...");
        
        Debug.logWarning("Sending ws request to URL [" + wsurl + "] and path [" + wspath + "]...", MODULE);
        
        Response res = null;
        InventoryItems iiresp = null;
        int responseCode = -1;
        String iistring = null;
        
        try {
            
            WebClient client = WebClient.create(wsurl);
            
            client.path(wspath);
            client.type(MediaType.APPLICATION_XML).accept(MediaType.APPLICATION_XML);
            
            try {
                res = client.put(inventoryItemRequestObj);
            }catch(Exception e) {
                String msg = "Cannot send PUT request for ws url [ " + wsurl + "]. Error is => "+e.getMessage() + ". Proceeding anyway..." ;
                if(useCustomLogger) {
                    logger.logError(msg);
                    logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
                }
                
                Debug.logError(msg, MODULE);
                
                if(wsEmailNotifyEnabled) {
                    StringBuilder emailBody = new StringBuilder();
                    emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - WS MpCommerce WS Availability"));
                    emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));

                    String emailSubject = "ERROR - WS " + wspath;
                    
                    MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
                
                }
                    
                return "success";
            }
           
            responseCode = res.getStatus();

            if(responseCode == 200) {
            
                /* The direct unmarshall does not work here. 
                 So I read the response as a string and unmarshall it separately.
                iiresp = res.readEntity(InventoryItems.class);
                */
                
                if(useCustomLogger) logger.logInfo("Receiving response from ws get-inventory...");
                //Debug.logWarning("Receiving response from ws get-inventory..parsing", MODULE);

                iistring = res.readEntity(String.class);
                
                if(useCustomLogger) logger.logInfo("=> WS String response : " + iistring);
                Debug.logWarning(" => WS String response : " + iistring, MODULE);
                
            }else{
                
                String msg = "Response status code is => " + responseCode + " / Response status info : " + res.getStatusInfo().getReasonPhrase();
                String msg2 = "Endpoint Url : " + wsurl + " - Resource Path: " + wspath;
                 if(useCustomLogger) logger.logError(msg + " - "+msg2);
                Debug.logError(msg, MODULE);
                
                if(wsEmailNotifyEnabled) {
                    
                    StringBuilder emailBody = new StringBuilder();
                    emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - MpCommerce WS Availability"));
                    emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine(msg2)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));
                
                    String emailSubject = "ERROR - WS " + wspath;
                    
                    MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
                
                }
                
                 return "success";
            }
        
        }catch(ClientErrorException e) {
            String msg = "*** Cannot read response. Error is => " + e.getMessage();
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg,MODULE);
            
            if(wsEmailNotifyEnabled) {
            
                StringBuilder emailBody = new StringBuilder();

                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - WS MpCommerce WS Availability"));
                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));

                String emailSubject = "ERROR - WS " + wspath;
                
                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
                
            }
            
            return "success";
            
        }catch(Exception e) {
            String msg = "*** Generic error with cxf WebClient for ws url [ "+ wsurl + "]. Error is => " + e.getMessage();
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            
            if(wsEmailNotifyEnabled) {
            
                StringBuilder emailBody = new StringBuilder();

                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - WS MpCommerce WS Availability"));
                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));

                String emailSubject = "ERROR - WS " + wspath;
                
                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
                
            }
            
            return "success";
            
        }
        
        /*
        File f = new File("/home/equake58/Documents/MPSTYLE/ABRAHAM/OMNICHANNEL/CHECK_AVAIL_WS/InventoryItem_Response_LC.xml");
         */
        
        JAXBContext jaxbContext = null;
        
        try {
            jaxbContext = JAXBContext.newInstance(InventoryItems.class.getPackage().getName());
        } catch (JAXBException ex) {
            String msg = "Error in creating JAXBContext. Error is => "+ex.getMessage();
            msg = msg + "\n Proceding anyway with order processing.";
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError(msg, MODULE);
            return "success";
        }
        
        Unmarshaller u = null;
        try {
            u = jaxbContext.createUnmarshaller();
        } catch (JAXBException ex) {
            String msg = "Error in creating Unmarshaller. Error is => "+ex.getMessage();
            msg = msg + "\n Proceding anyway with order processing.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            return "success";
        }
        
        if(useCustomLogger) logger.logInfo("Unmarshalling response into InventoryItems object");
        try {
            StringReader reader = new StringReader(iistring);
            iiresp = (InventoryItems) u.unmarshal(reader);
        } catch (JAXBException ex) {
            String msg = "Error in unmarshalling repsonse into InventoryItems object. Error is => "+ex.getMessage();
            msg = msg + "\n Proceding anyway with order processing.";
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError(msg, MODULE);
            return "success";
        }
       
        
        orderItemAvailabilityMap = MpStyleWSHelper.updateInventoryItemsAvailability(iiresp, otherStoreFacilities, delegator, dispatcher);
        
        
        }else{
        	
        	for(ShoppingCartItem cartItem : cartItems) {
                
                //index of a list starting from 0: add 1 to correctly convert into and order item seq id
                int itemIndex = cart.getItemIndex(cartItem) + 1;
                String orderItemSeqId = UtilFormatOut.formatPaddedNumber(itemIndex, 5);
                
                orderItemCartObjectMap.put(orderItemSeqId, cartItem);
        	
        	}
            
        	Debug.logWarning(" =>  orderItemCartObjectMap : " + UtilMisc.printMap( orderItemCartObjectMap), MODULE);
        	
        	
            File f = new File("/home/paolo/Scrivania/OUT/InventoryItems_Response.xml");
            JAXBContext jaxbContext = null;
            InventoryItems iiresp = null;
            
            try {
                jaxbContext = JAXBContext.newInstance(InventoryItems.class.getPackage().getName());
            } catch (JAXBException ex) {
                Logger.getLogger(MpStyleWSEvents.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            Unmarshaller u = null;
            try {
                u = jaxbContext.createUnmarshaller();
            } catch (JAXBException ex) {
                Logger.getLogger(MpStyleWSEvents.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
            	iiresp = (InventoryItems) u.unmarshal(f);
            } catch (JAXBException ex) {
                Logger.getLogger(MpStyleWSEvents.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            orderItemAvailabilityMap = MpStyleWSHelper.updateInventoryItemsAvailability(iiresp, otherStoreFacilities, delegator, dispatcher);
            
        }
        
        
        List<String> itemRemovedList = new ArrayList<>();
        
        if(orderItemAvailabilityMap != null && !orderItemAvailabilityMap.isEmpty()) {
            
            Debug.logWarning("### After ws update availability map => "+ UtilMisc.printMap(orderItemAvailabilityMap), MODULE);
            
            //loop the items and remove from cart out of stock products
            for(Map.Entry<String, Object> entry : orderItemAvailabilityMap.entrySet()) {
                
                String orderItemSeqId_k = entry.getKey();
                
                ShoppingCartItem cartItem = (ShoppingCartItem) orderItemCartObjectMap.get(orderItemSeqId_k);
                
                Debug.logWarning("### cartItem => "+cartItem, MODULE);
                
                //get the cartLine index
                int itemIndex = cart.getItemIndex(cartItem);
                
                Map<String, Object> cartItemAvailMap_v = (Map<String, Object>) entry.getValue();
                
                Debug.logWarning("Checking cart item id: "+orderItemSeqId_k+", item index: "+itemIndex, MODULE);
                
                if(useCustomLogger) {
                    logger.logInfo("----- Cart Item [ " + orderItemSeqId_k + " ] -----");
                    logger.logInfo("- Item sku" + (String)cartItemAvailMap_v.get("sku"));
                    logger.logInfo("Item in stock?" + (Boolean)cartItemAvailMap_v.get("instock"));
                }
                    
                //Remove item from cart
                List<ShoppingCartItem> items = cart.items();
                
                if(!(Boolean)cartItemAvailMap_v.get("instock")) {
                	
                    try {
                        cart.removeCartItem(itemIndex, dispatcher);
                        cartModified = true;
                        
                        Debug.logWarning("===> Cart item with original sequence id "+orderItemSeqId_k+" and product id [" + cartItem.getProductId() + "] removed from cart for out of stock", MODULE);
                        if(useCustomLogger) logger.logInfo("===> Cart item with original sequence id "+orderItemSeqId_k+" and product id [" + cartItem.getProductId() + "] removed from cart for out of stock");
                        itemRemovedList.add(UtilProperties.getMessage(RESOURCE_ERROR, "ws.productOutOfStock", new Object[] {(String)cartItemAvailMap_v.get("sku"), itemIndex}, locale));
                    } catch (CartItemModifyException ex) {
                        String msg = "Error in removing item with sequence id ["+orderItemSeqId_k+"] from cart. Error is => " + ex.getMessage();
                        Debug.logError(msg, MODULE);
                        if(useCustomLogger) logger.logError(msg);
                        request.setAttribute("_ERROR_MESSAGE_", msg);
                    }
                }
                
            }

        }

        //Cart has been modified: items have bben removed from cart
        if(cartModified) {
        	
        	Debug.logWarning("### cartModified: "+cartModified, MODULE);
        	
        	Debug.logWarning("### itemRemovedList: "+itemRemovedList, MODULE);
        	
            request.setAttribute("_EVENT_MESSAGE_LIST_", itemRemovedList);
            result = "error";
            
        }else{
            result = "success";
        }
        
        if(useCustomLogger) logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
        
        Debug.logWarning("### result: "+result, MODULE);
        
        return result;
        
    }
    
    
}//end class
