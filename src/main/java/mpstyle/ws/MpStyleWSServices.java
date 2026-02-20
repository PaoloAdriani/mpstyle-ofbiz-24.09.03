/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.ws;

import mpstyle.log.MpStyleLogger;
import mpstyle.util.MpStyleUtil;
import mpstyle.util.email.MpEmailServices;
import mpstyle.util.email.MpEmailUtil;
import mpstyle.util.http.MpHttpUtil;
import mpstyle.util.order.MpOrderHelper;
import mpstyle.ws.order.*;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.order.order.OrderReadHelper;
import org.apache.ofbiz.party.party.PartyWorker;
import org.apache.ofbiz.product.product.ProductWorker;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.cxf.jaxrs.client.WebClient;


/**
 *
 * @author equake58
 */
public class MpStyleWSServices {
    
    public static final String MODULE = MpStyleWSServices.class.getName();
    private static final String OMNI_SYSTEM_RESOURCE_ID = "mpomni";
    
    public static Map<String, Object> createMpStyleOrder(DispatchContext dctx, Map<String, Object> context) {
        
        final String method = "createMpStyleOrder";
        
        Locale locale = (Locale) context.get("locale");
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        
        boolean wsEmailNotifyEnabled = false;
        boolean useCustomLogger = false;
        
        String env = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsorder.environment", delegator);
        
        Debug.logWarning("*** env: "+env, MODULE);
        
        boolean test = Boolean.valueOf(env);
        
        Debug.logWarning("*** test: "+test, MODULE);
        
        String activeMpOrder = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "activeMpstyleErpOrder",delegator);
        
        Debug.logWarning("*** activeMpOrder: "+activeMpOrder, MODULE);
        
        boolean isActive =  Boolean.valueOf(activeMpOrder);
        
        Debug.logWarning("*** isActive: "+isActive, MODULE);
        
        if(!isActive)
        {
        	return ServiceUtil.returnSuccess();
        }
        
        
        String orderId = (String) context.get("orderId");
        Debug.logWarning("*** HELLO WORLD! I AM THE SERVICE <createMpStyleOrder> TRIGGERED WITH A SECA ON SERVICE changeOrderStatus for order "+orderId+" ***", MODULE);

        //Reading ws url from system properties
        //String mp_ws_order_url = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "mpomni.mp_ws_order_url", delegator);
        
        //user name and password for services
        String logfilename = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsorder.logfilename", delegator);
        String logdirpath = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsorder.logdirpath", delegator);
        String wsurl = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsorder.url", delegator);
        String wspath = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "wsorder.path", delegator);
        String wsSeasons = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "mpWsSeasons", delegator);
        String wsFacilities = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "mpWsFacilities", delegator);
        String username = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "serviceUsername", delegator);
        String password = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "servicePassword", delegator);
        String wsMailNotify = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "ws.mail.notify", delegator);
        
        if(wsMailNotify != null) 
        {
            wsEmailNotifyEnabled = "Y".equals(wsMailNotify);
        }
        
        String wsMailFromAddress = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "ws.mail.fromAddress", delegator);
        String wsMailToAddress = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "ws.mail.toAddress", delegator);
        String wsMailCcAddress = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "ws.mail.ccAddress", delegator);
        
        
        
        if(test) 
        {
            Debug.logWarning("*** order id: "+orderId, MODULE);
            Debug.logWarning("*** mp ws url: "+ wsurl, MODULE);
            Debug.logWarning("*** mp ws path: "+ wspath, MODULE);
            Debug.logWarning("*** mp ws seasons: "+ wsSeasons, MODULE);
            Debug.logWarning("*** mp ws facilities: "+ wsFacilities, MODULE);
            Debug.logWarning("*** mp ws email notify: "+ wsMailNotify, MODULE);
            Debug.logWarning("*** service username: "+username, MODULE);
            Debug.logWarning("*** service password: "+password, MODULE);
            Debug.logWarning("*** ws order logdir: "+logdirpath, MODULE);
            Debug.logWarning("*** ws order logfile: "+logfilename, MODULE);
        }
        
        //Creation of the custom logger file
        MpStyleLogger logger = null;
        
        if(logfilename == null  || UtilValidate.isEmpty(logfilename.trim()) || logdirpath == null || UtilValidate.isEmpty(logdirpath.trim())) {
            Debug.logWarning("Missing system properties [mpomni/wsorder.logfilename] and [mpomni/wsorder.logdirpath]. Cannot use custom logger file. Using standard only.", MODULE);
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
            return ServiceUtil.returnError(msg);
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
            return ServiceUtil.returnSuccess(msg);
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
            return ServiceUtil.returnSuccess(msg);
        }
        
        ArrayList<String> enabledSeasons = new ArrayList(Arrays.asList(seasonArray));
        
        /*
        If no facilities are enabled for use with ws, return success and do nothing.
        */
        if(wsFacilities == null || UtilValidate.isEmpty(wsFacilities.trim())) {
            
            String msg = "Facilities not enabled for ws. Check SystemProperty [mpomni/mpWsFacilities]. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnSuccess(msg);
        }
        
        wsFacilities = wsFacilities.trim();
        
        //split facilities using commas
        String []facilitiesArray = wsFacilities.split(",");
        
        if(facilitiesArray.length <= 0) {
            String msg = "Splitted facilities array is empty. Check the SystemProperty [mpomni/mpWsFacilities]; put in values separated by commas. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnSuccess(msg);
        }
        
        ArrayList<String> enabledFacilities = new ArrayList(Arrays.asList(facilitiesArray));
        
        /* If ws url is not set cannot perform the call. */
        if(wsurl == null || UtilValidate.isEmpty(wsurl.trim())) {
            
            String msg = "*** WebService URL for order reservation is not set as SystemProperty [mpomni/wsorder.url]. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            
            if(wsEmailNotifyEnabled) {
                
                StringBuilder emailBody = new StringBuilder();
                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - MpCommerce WS Order Reservation"));
                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));

                String emailSubject = "ERROR - WS " + wspath;

                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
                
            }
            
            return ServiceUtil.returnError(msg);
        }
        
        wsurl = wsurl.trim();
        
        /* If ws path is not set cannot perform the call. */
        if(wspath == null || UtilValidate.isEmpty(wspath.trim())) {
            
            String msg = "*** WebService Path for order reservation is not set as SystemProperty [mpomni/wsorder.path]. Not doing the call.";
            
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            
            Debug.logError(msg, MODULE);
            
            if(wsEmailNotifyEnabled) {
                
                StringBuilder emailBody = new StringBuilder();
                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - MpCommerce WS Order Reservation"));
                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));

                String emailSubject = "ERROR - WS " + wspath;

                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
                
            }
            
            return ServiceUtil.returnError(msg);
        }
        
        wspath = wspath.trim();
        
        
        /* ##### Check orderId ##### */
        if(orderId == null) {
            String msg = "Parameter orderId not found in service context. Do not call ws.";
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnError(msg);
        }
        
        //Retrieve orderHeader and orderItems (APPROVED)
        OrderReadHelper orh = new OrderReadHelper(delegator, orderId);
        
        GenericValue orderHeader = orh.getOrderHeader();
        
        String orderStatus = orderHeader.getString("statusId");
        
        //check order type
        if(!"SALES_ORDER".equals(orh.getOrderTypeId())) {
            String msg = "Order ["+orderId+"] is not a SALES_ORDER. Do not call ws.";
            if(useCustomLogger) {
                logger.logInfo(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logWarning(msg, MODULE);
            return ServiceUtil.returnSuccess(msg);
        }
        
        
        List<GenericValue> approvedItems = orh.getOrderItemsByCondition(EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_APPROVED"));
        
        if(UtilValidate.isEmpty(approvedItems)) {
            
            String msg = "No APPROVED items found for order ["+orderId+"]. Do not call ws.";
            if(useCustomLogger) {
                logger.logInfo(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnSuccess(msg);
        }
        
        //Retrieve a list of all the reservation for this order
        List<GenericValue> allOrderItemReservation = new ArrayList<>();
        
        //Retrieve all the reservation in place for the approved order items
        for(GenericValue orderItem : approvedItems) {
            
            List<GenericValue> ordItemInvResList = orh.getOrderItemShipGrpInvResList(orderItem);
            
            if(UtilValidate.isNotEmpty(ordItemInvResList)) {
                allOrderItemReservation.addAll(ordItemInvResList);
            }
            
        }
        
        //Quit if I do not have any reservation
        if(UtilValidate.isEmpty(allOrderItemReservation)) {
            String msg = "No order item inventory reservations found for order ["+orderId+"]. Do not call ws.";
            if(useCustomLogger) {
                logger.logInfo(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnSuccess(msg);
        }
        
        /*
         * Loop the reservation and check reservation facility against the enabled facilities for ws.
         * Skip item reservation rows for facilities that are not enabled.
         */
        List<GenericValue> enabledFacilitiesReservationList = new ArrayList<>();
        
        for(GenericValue invItemRes : allOrderItemReservation) {
           
            GenericValue _invItem = null;

            try {
                _invItem = invItemRes.getRelatedOne("InventoryItem", false);
            } catch (GenericEntityException ex) {
                String msg = "Error in retrieving InventoryItem relation for inventory reservation record ["+invItemRes+"]. Error is => " + ex.getMessage();
                if(useCustomLogger) {
                    logger.logInfo(msg);
                    logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
                }
                Debug.logError(ex, msg, MODULE);
                return ServiceUtil.returnError(msg);
            }

            String _invFacilityId = (String) _invItem.get("facilityId");
            
            GenericValue _facility = null;
            
            try {
                _facility = delegator.findOne("Facility", UtilMisc.toMap("facilityId", _invFacilityId), false);
                
            }catch(GenericEntityException gee) {
                String msg = "Error in retrieving facility with id ["+_invFacilityId+"]. Error is => " + gee.getMessage();
                if(useCustomLogger) {
                    logger.logInfo(msg);
                    logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
                }
                Debug.logError(gee.getMessage(), MODULE);
                return ServiceUtil.returnError(msg);
            }
            
            if(_facility != null) {
                
                if(enabledFacilities.contains((String) _facility.get("facilityId"))) {
                    enabledFacilitiesReservationList.add(invItemRes);
                }
                
            }
            
        }
        
        if(enabledFacilitiesReservationList.isEmpty()) {
            String msg = "No reservations found for enabled ws facilities. Do not call ws.";
            if(useCustomLogger) {
                logger.logWarning(msg);
                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logWarning(msg, MODULE);
            return ServiceUtil.returnSuccess(msg);
        }
        
        if(useCustomLogger && test) logger.logInfo("Found reservations for enabled facilities. Prepare ws order call.");
        
        //grouping by order_itm seq id (just in case an order item has different reservations) 
        Map<String, List<GenericValue>> reservPerOrderItem = new LinkedHashMap<>();
        
        for(GenericValue facilityRes : enabledFacilitiesReservationList) {
            
            String orderItemSeqId = (String) facilityRes.get("orderItemSeqId");
            List<GenericValue> itemReservationList = null;
            
            if(reservPerOrderItem.get(orderItemSeqId) != null) {
                
                itemReservationList = reservPerOrderItem.get(orderItemSeqId);
                
                itemReservationList.add(facilityRes);
                
            }else {
                
                itemReservationList = new ArrayList<>();
                itemReservationList.add(facilityRes);
                
            }
            
            reservPerOrderItem.put(orderItemSeqId, itemReservationList);
            
        }
        
        //Prepare the java object to marshal into XML
        CreateOrderDeliveryAddress deliveryAddress = new CreateOrderDeliveryAddress();
        
        GenericValue customerShippingAddress = orh.getShippingAddress("00001");
        
        String address = "";
        String city = "";
        String postalCode = "";
        String nation = "";
        
        if (!customerShippingAddress.isEmpty()) 
        {
            address = customerShippingAddress.getString("address1");
            city = customerShippingAddress.getString("city");
            postalCode = customerShippingAddress.getString("postalCode");
            nation = customerShippingAddress.getString("countryGeoId");
        }
        
        deliveryAddress.setAddress(address);
        deliveryAddress.setZip(postalCode);
        deliveryAddress.setCity(city);
        deliveryAddress.setCountry(nation);
        
        GenericValue endUserParty = orh.getEndUserParty(); // retrieve customer information.
        
        CreateOrderRequestCustomer customer = new CreateOrderRequestCustomer();
        
        customer.setId(endUserParty.getString("partyId"));
        customer.setName(endUserParty.getString("firstName")+" "+endUserParty.getString("lastName"));
        customer.setAddress(address);
        customer.setZip(postalCode);
        customer.setCity(city);
        customer.setCountry(nation);
        customer.setEmail(orh.getOrderEmailString());
        
        GenericValue partyTelecomNumber = PartyWorker.findPartyLatestTelecomNumber(endUserParty.getString("partyId"), delegator);
        
        if(partyTelecomNumber != null && !partyTelecomNumber.isEmpty())
        customer.setPhone(partyTelecomNumber.getString("contactNumber"));

        logger.logInfo("Create object CreateOrderRequestCustomer: "+customer.toString());
        
        Set<Entry<String, List<GenericValue>>> resEntrySet = reservPerOrderItem.entrySet();
        
        //Aux map: key => orderItemSeqId; value => map<facilityId, sum(res_qty_on_facilityId)>
        Map<String, Map<String, BigDecimal>> ordItmFacilityResTotal = new HashMap<>();
        
        //entry.getKey() is orderItemSeqId
        for(Entry<String, List<GenericValue>> entry : resEntrySet) {
            
            String _orderItemSeqId = entry.getKey();
            List<GenericValue> _orderItemResList = entry.getValue();
            
            //Aux map: reservation total per facility
            Map<String, BigDecimal> resTotalPerFacility = new HashMap<>();
            
            for(GenericValue _itemRes : _orderItemResList) {
                
                String _resFacilityId = MpOrderHelper.getInventoryReservationFacility(_itemRes, delegator);
                
                if(resTotalPerFacility.get(_resFacilityId) != null) {
                    BigDecimal _tmpQty = resTotalPerFacility.get(_resFacilityId);
                    _tmpQty = _tmpQty.add(_itemRes.getBigDecimal("quantity"));
                    resTotalPerFacility.put(_resFacilityId, _tmpQty);
                }else{
                    resTotalPerFacility.put(_resFacilityId, _itemRes.getBigDecimal("quantity"));
                }
                
            }
            
            ordItmFacilityResTotal.put(_orderItemSeqId, resTotalPerFacility);
            
        }
        
        Set<Entry<String, Map<String, BigDecimal>>> ordItmFacilityResEntrySet = ordItmFacilityResTotal.entrySet();
        
        List<CreateOrderRequestItem> itemList = new ArrayList<CreateOrderRequestItem>();
        
        for(Entry<String, Map<String, BigDecimal>> entry : ordItmFacilityResEntrySet) {
            
            Debug.logWarning("Reservation total by facility for order item => "+UtilMisc.printMap(ordItmFacilityResTotal), MODULE);
            
            String _orderItemSeqId = entry.getKey();
            Map<String, BigDecimal> itmResPerFacilityMap = entry.getValue();
            
            GenericValue _orderItem = orh.getOrderItem(_orderItemSeqId);
            String _productId = (String) _orderItem.get("productId");

            String seasonCode = _productId.substring(0, 2);
          	 
        	String lineCode = _productId.substring(2,3);
        	 
        	String articleCode = _productId.substring(3, 9);
        
        	String colorCode = _productId.substring(9, _productId.indexOf("."));
        	
        	String tg = _productId.substring(_productId.indexOf(".")+1);
            
            GenericValue product = null;
            //List<GenericValue> goodIdentificationList = null;
            
            GenericValue pfApplResult = null;
            
            try 
			{
            
            	product = delegator.findOne("Product", UtilMisc.toMap("productId", _productId), false);
            	
            	//goodIdentificationList = product.getRelated("GoodIdentification", null, null, false);
            
			
            	GenericValue parentProduct = ProductWorker.getParentProduct(_productId, delegator);
            
            	EntityCondition productFeatureApplCondition = EntityCondition.makeCondition(EntityOperator.AND,
                    EntityCondition.makeCondition("productId", EntityOperator.EQUALS, parentProduct.getString("productId")),
                    EntityCondition.makeCondition("productFeatureId", EntityOperator.EQUALS, tg));
            
            	List<GenericValue> productFeatureApplList = delegator.findList("ProductFeatureAppl",productFeatureApplCondition , null, null, null, false);
            
            	List<GenericValue> prodFeatApplFiltered = null;
            	
            
            	if(productFeatureApplList != null && !productFeatureApplList.isEmpty())
            	{
            		prodFeatApplFiltered = EntityUtil.filterByDate(productFeatureApplList);
            	
            		pfApplResult = EntityUtil.getFirst(prodFeatApplFiltered);
            	
            	}
			
			} catch (GenericEntityException gee) {
				
				Debug.logError(gee, MODULE);
			}
            
            /*
            String ean = null;
            
            if (!UtilValidate.isEmpty(goodIdentificationList)) 
            {
            	for (GenericValue goodIdentification: goodIdentificationList) 
            	{
                    ean = goodIdentification.getString("idValue");
                }
            }
            */
            
            BigDecimal oiSt = orh.getOrderItemSubTotal(_orderItem);
            
            for(Entry<String, BigDecimal> _e :  itmResPerFacilityMap.entrySet()) {
            
            	BigDecimal bg = _e.getValue();
            	
            	CreateOrderRequestItem item = new CreateOrderRequestItem();
            	
            	item.setSeasonId(seasonCode);
            	item.setCollectionId(lineCode);
            	item.setProductId(articleCode);
            	item.setColorId(colorCode);
            	
            	// recupero taglia
            	Long sequenceNum = pfApplResult.getLong("sequenceNum");
            	
            	if(sequenceNum.intValue() == 1)
            	{
            		item.setSize01(bg.intValue());
            		
            	}
            	
            	if(sequenceNum.intValue() == 2)
            	{
            		item.setSize02(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 3)
            	{
            		item.setSize03(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 4)
            	{
            		item.setSize04(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 5)
            	{
            		item.setSize05(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 6)
            	{
            		item.setSize06(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 7)
            	{
            		item.setSize07(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 8)
            	{
            		item.setSize08(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 9)
            	{
            		item.setSize09(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 10)
            	{
            		item.setSize10(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 11)
            	{
            		item.setSize11(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 12)
            	{
            		item.setSize12(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 13)
            	{
            		item.setSize13(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 14)
            	{
            		item.setSize14(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 15)
            	{
            		item.setSize15(bg.intValue());
            	}
            	
            	if(sequenceNum.intValue() == 16)
            	{
            		item.setSize16(bg.intValue());
            	}
            	
            	item.setPrice(oiSt.doubleValue());
            	
            	itemList.add(item);
            	
            	logger.logInfo("Create object CreateOrderRequestItem: "+item.toString());
            	
            }
        }
        
        CreateOrderRequestOrder order = new CreateOrderRequestOrder();
        
        order.setId(orderId);        
        order.setTotal(orh.getOrderGrandTotal().doubleValue());
        order.setShippingCost(orh.getShippingTotal().doubleValue());
    
        order.setStatus(orderHeader.getString("statusId"));
        order.setCustomer(customer);
        order.setAddress(deliveryAddress);
        order.setItems(itemList);
        
        logger.logInfo("Create object CreateOrderRequestOrder: "+order.toString());
        
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setOrder(order);
        
        //Marshall Java Object to XML for the ws call
        String xmlContent = null;
        
        if(!test)
        {
        
	        try
	        {
	            //Create JAXB Context
	        	JAXBContext jaxbContext = JAXBContext.newInstance(CreateOrderRequest.class);
	             
	            //Create Marshaller
	            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
	            
	            //Add  pretty print formatting for xml
	            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
	
	            //Print XML String to Console
	            StringWriter sw = new StringWriter();
	             
	            //Write XML to StringWriter
	            jaxbMarshaller.marshal(orderRequest, sw);
	             
	            //Verify XML Content
	            xmlContent = sw.toString();
	            if(useCustomLogger) logger.logInfo("XML Order Request obj => "+xmlContent);
	            Debug.logWarning("### Marshalled OrderReservation Object: "+ xmlContent, MODULE );
	 
	        } catch (JAXBException e) {
	            String msg = "Error in JAXB Marshalling of entity OrderReservation into XML. Cannot call WS. ["+e.getMessage()+"].";
	            if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            Debug.logError(msg, MODULE);
	            return ServiceUtil.returnError(msg);
	        }
	        
	        /* testing the wsurl: check if is valid and available */
	        if(!MpHttpUtil.checkWebResourceURI(wsurl))
	        {
	        	String msg = "*** Problems in testing connection for URL ["+ wsurl + "]. Check if URL exists or you are connected to Internet. Do not call ws. ***";
	            
	        	if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            
	        	Debug.logError(msg, MODULE);
	            
	            if(wsEmailNotifyEnabled) 
	            {
	                StringBuilder emailBody = new StringBuilder();
	                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - WS MpCommerce WS Order Reservation"));
	                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));
	
	                String emailSubject = "ERROR - WS " + wspath;
	
	                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
	
	            }
	            
	            return ServiceUtil.returnError(msg);
	            
	        } 
	        
	         /* ##### REST REQUEST PHASE ##### */
	        if(useCustomLogger) logger.logInfo("Sending ws request to URL [" + wsurl + "] and path [" + wspath + "]...");
	        
	        Debug.logWarning("Sending ws request to URL [" + wsurl + "] and path [" + wspath + "]...", MODULE);
	        
	        Response res = null;
	        CreateOrderResponse coresp = null;
	        int responseCode = -1;
	        String orstring = null;
	        
	        try 
	        {
	            WebClient client = WebClient.create(wsurl);
	            
	            client.path(wspath);
	            client.type(MediaType.APPLICATION_XML).accept(MediaType.APPLICATION_XML);
	            
	            try 
	            {
	                
	            	res = client.put(orderRequest);
	            
	            }catch(Exception e) {
	                
	            	String msg = "Cannot send PUT request for ws url [ " + wsurl + "]. Error is => "+e.getMessage() + "." ;
	                
	            	if(useCustomLogger) 
	            	{
	                    logger.logError(msg);
	                    logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	                }
	                
	                Debug.logError(msg, MODULE);
	                
	                if(wsEmailNotifyEnabled) 
	                {
	                	StringBuilder emailBody = new StringBuilder();
	                    emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - WS MpCommerce WS Order Reservation"));
	                    emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));
	
	                    String emailSubject = "ERROR - WS " + wspath;
	                    
	                    MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
	                
	                }
	                    
	                return ServiceUtil.returnError(msg);
	            }
	            
	            responseCode = res.getStatus();
	            
	            if(responseCode == 200) 
	            {
	                if(useCustomLogger) logger.logInfo("Receiving response from ws...");
	                
	                orstring = res.readEntity(String.class);
	                
	                if(useCustomLogger) logger.logInfo("=> WS String response : " + orstring);
	                Debug.logWarning(" => WS String response : " + orstring, MODULE);
	                
	            }else{
	                
	                String msg = "Response status code is => " + responseCode + " / Response status info : " + res.getStatusInfo().getReasonPhrase();
	                String msg2 = "Endpoint Url : " + wsurl + " - Resource Path: " + wspath;
	                
	                if(useCustomLogger) logger.logError(msg + " - "+msg2);
	                
	                Debug.logError(msg, MODULE);
	                
	                if(wsEmailNotifyEnabled) 
	                {
	                    StringBuilder emailBody = new StringBuilder();
	                    emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - MpCommerce WS Order Reservation"));
	                    emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine(msg2)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));
	                
	                    String emailSubject = "ERROR - WS " + wspath;
	                    
	                    MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
	                    
	                    return ServiceUtil.returnError(msg);
	                
	                }
	                
	            }
	            
	        }catch(ClientErrorException e){
	            
	            String msg = "*** Cannot read response. Error is => " + e.getMessage();
	            
	            if(useCustomLogger) 
	            {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            
	            Debug.logError(msg,MODULE);
	            
	            if(wsEmailNotifyEnabled) 
	            {
	                StringBuilder emailBody = new StringBuilder();
	
	                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - WS MpCommerce WS Order Reservation"));
	                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));
	
	                String emailSubject = "ERROR - WS " + wspath;
	                
	                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
	            }
	            
	            return ServiceUtil.returnError(msg);
	            
	        }catch(Exception e) {
	            
	            String msg = "*** Generic error with cxf WebClient for ws url [ "+ wsurl + "]. Error is => " + e.getMessage();
	            
	            if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            
	            Debug.logError(msg, MODULE);
	            
	            if(wsEmailNotifyEnabled) 
	            {
	                StringBuilder emailBody = new StringBuilder();
	
	                emailBody.append(MpEmailUtil.createHtmlBodyTitle("ERROR - WS MpCommerce WS Order Reservation"));
	                emailBody.append("\n").append(MpEmailUtil.createHtmlTextLine(msg)).append("\n").append(MpEmailUtil.createHtmlTextLine("Date/Time: "+ MpStyleUtil.getNowDateTimeString()));
	
	                String emailSubject = "ERROR - WS " + wspath;
	                
	                MpEmailServices.sendSimpleMail(emailBody.toString(), wsMailFromAddress, wsMailToAddress, wsMailCcAddress, null, emailSubject, "text/html", username, password, dispatcher);
	                
	            }
	            
	            return ServiceUtil.returnError(msg);
	            
	        }
	        
	        /* ##### UNMARSHALLING RESPONSE ##### */
	        JAXBContext jaxbContext = null;
	        
	        try 
	        {
	            
	        	jaxbContext = JAXBContext.newInstance(CreateOrderResponse.class);
	        
	        } catch (JAXBException ex) {
	            
	        	String msg = "Error in creating JAXBContext. Error is => "+ex.getMessage();
	            
	            if(useCustomLogger) 
	            {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            
	            Debug.logError(msg, MODULE);
	            return ServiceUtil.returnError(msg);
	        }
	        
	        Unmarshaller u = null;
	        
	        try {
	           
	        	u = jaxbContext.createUnmarshaller();
	        
	        } catch (JAXBException ex) {
	            
	        	String msg = "Error in creating Unmarshaller. Error is => "+ex.getMessage();
	           
	            if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            
	            Debug.logError(msg, MODULE);
	            return ServiceUtil.returnError(msg);
	        }
	        
	        if(useCustomLogger) logger.logInfo("Unmarshalling response into CreateOrderResponse object");
	        
	        try {
	            
	        	StringReader reader = new StringReader(orstring);
	            
	        	coresp = (CreateOrderResponse) u.unmarshal(reader);
	        
	        } catch (JAXBException ex) {
	            String msg = "Error in unmarshalling response into MpOrderResponse object. Error is => "+ex.getMessage();
	            if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            Debug.logError(msg, MODULE);
	            return ServiceUtil.returnError(msg);
	        }
	        
	        CreateOrderResponseOrder orderResponse = coresp.getOrder();
	        
	        Debug.logWarning(" *** CreateOrderResponseOrder: *** "+orderResponse.toString(), MODULE);
	        logger.logInfo(" *** CreateOrderResponseOrder: *** "+orderResponse.toString());
	        
	        String erpOrderId = orderResponse.getErpOrderId();
	        
	        String status = orderResponse.getStatus();
	        
	        String status_description = orderResponse.getStatusDescription();
	        
	        String msg = "Erp order id from Mpstyle is => " +erpOrderId+ " with this Status: "+status+ " and these details: "+status_description;
	        
	    	List<CreateOrderResponseItem> ordItemRespList = orderResponse.getItems();
	    	
	    	List<GenericValue> orderItems = orh.getOrderItems();
	    	
	    	for(GenericValue orderItem : orderItems)
	    	{
	    		String productId = null;
	    		
	    		for(CreateOrderResponseItem cori : ordItemRespList)
	    		{
	    			Debug.logWarning(" *** CreateOrderResponseItem: *** "+cori.toString(), MODULE);
	    			
	    			logger.logInfo("*** CreateOrderResponseItem: *** "+cori.toString());
	    			
	    			StringBuffer sbProduct = new StringBuffer();
	    			sbProduct.append(cori.getSeasonId()).append(cori.getCollectionId()).append(cori.getProductId()).append(cori.getColorId()).append(".").append(cori.getSize());
	    			
	    			productId = sbProduct.toString();
	    			
	    			BigDecimal orderRow = cori.getOrderRow();
	    			
	    			StringBuffer sbTotalExtId = new StringBuffer();
	    			sbTotalExtId.append(erpOrderId).append("/").append(orderRow);
	    			
	    			Debug.logWarning("*** productId: ***"+productId, MODULE);
	    			logger.logInfo("*** productId from response object: *** "+productId);
	    			
	    			BigDecimal qtaOrdItem = new BigDecimal(cori.getQtaOrd());
	    			
	    			if(orderItem.getString("productId").equals(productId) && orderItem.getBigDecimal("quantity").compareTo(qtaOrdItem) == 0)
	    			{
	    				Debug.logWarning("*** prepare to store erpOrderId into externalId ...", MODULE);
	    				logger.logInfo("*** prepare to store erpOrderId into externalId ...");
	    				
	    				orderItem.put("externalId", sbTotalExtId.toString());
	    				try {
							orderItem.store();
							
							Debug.logWarning("*** erpOrderId stored! ", MODULE);
							logger.logInfo("*** erpOrderId stored! ");
							
						} catch (GenericEntityException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
	    			}
	    			
	    		}
	    	}
	    	
	    	Debug.logWarning("*** prepare to store mpOrderId...", MODULE);
			logger.logInfo("*** prepare to store mpOrderId...");
	    	
	    	orderHeader.put("mpOrderId", erpOrderId);
	    	try {
	    		orderHeader.store();
	    		
	    		Debug.logWarning("*** mpOrderId stored! ", MODULE);
				logger.logInfo("*** mpOrderId stored! ");
	    		
			} catch (GenericEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
	        
	        if(useCustomLogger) logger.logInfo(msg);
	        Debug.logWarning(msg, MODULE);
	        
	        if(useCustomLogger) logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	        
	        return ServiceUtil.returnSuccess(msg);
	        
	    //if(!test)    
        }else {
        	
        	
        	try
	        {
	            //Create JAXB Context
	        	JAXBContext jaxbContext = JAXBContext.newInstance(CreateOrderRequest.class);
	             
	            //Create Marshaller
	            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
	            
	            //Add  pretty print formatting for xml
	            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
	
	            //Print XML String to Console
	            StringWriter sw = new StringWriter();
	             
	            //Write XML to StringWriter
	            jaxbMarshaller.marshal(orderRequest, sw);
	             
	            //Verify XML Content
	            xmlContent = sw.toString();
	            if(useCustomLogger) logger.logInfo("XML Order Request obj => "+xmlContent);
	            Debug.logWarning("### Marshalled OrderReservation Object: "+ xmlContent, MODULE );
	 
	        } catch (JAXBException e) {
	            String msg = "Error in JAXB Marshalling of entity OrderReservation into XML. Cannot call WS. ["+e.getMessage()+"].";
	            if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            Debug.logError(msg, MODULE);
	            return ServiceUtil.returnError(msg);
	        }
	        
	                
	         /* ##### REST REQUEST PHASE ##### */
	        if(useCustomLogger) logger.logInfo("Sending ws request to URL [" + wsurl + "] and path [" + wspath + "]...");
	        
	        Debug.logWarning("Sending ws request to URL [" + wsurl + "] and path [" + wspath + "]...", MODULE);
	        
	        CreateOrderResponse coresp = null;

	        File file = new File("/home/paolo/Scrivania/IN/CreateOrderResponse.xml");
	        
	        /* ##### UNMARSHALLING RESPONSE ##### */
	        JAXBContext jaxbContext = null;
	        
	        try 
	        {
	        	jaxbContext = JAXBContext.newInstance(CreateOrderResponse.class);
	 
	        
	        } catch (JAXBException ex) {
	            
	        	String msg = "Error in creating JAXBContext. Error is => "+ex.getMessage();
	            
	        	if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            
	        	Debug.logError(msg, MODULE);
	            return ServiceUtil.returnError(msg);
	        }
	        
	        Unmarshaller u = null;
	        try 
	        {
	            u = jaxbContext.createUnmarshaller();
	        
	        } catch (JAXBException ex) {
	            
	        	String msg = "Error in creating Unmarshaller. Error is => "+ex.getMessage();
	           
	            if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            
	            Debug.logError(msg, MODULE);
	            return ServiceUtil.returnError(msg);
	        }
	        
	        if(useCustomLogger) logger.logInfo("Unmarshalling response into CreateOrderResponse object");
	        try 
	        {
	            coresp = (CreateOrderResponse) u.unmarshal(file);
	            
	            Debug.logWarning("************************coresp: "+coresp.toString(), MODULE);
	            
	        
	        } catch (JAXBException ex) {
	            String msg = "Error in unmarshalling response into CreateOrderResponse object. Error is => "+ex.getMessage();
	            if(useCustomLogger) {
	                logger.logError(msg);
	                logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	            }
	            Debug.logError(msg, MODULE);
	            return ServiceUtil.returnError(msg);
	        }
	        
	        CreateOrderResponseOrder orderResponse = coresp.getOrder();
	        
	        Debug.logWarning(" *** CreateOrderResponseOrder: *** "+orderResponse.toString(), MODULE);
	        logger.logInfo("*** CreateOrderResponseOrder: *** "+orderResponse.toString());
	        
	        String erpOrderId = orderResponse.getErpOrderId();
	        
	        String status = orderResponse.getStatus();
	        
	        String status_description = orderResponse.getStatusDescription();
	        
	        String msg = "Erp order id from Mpstyle is => " +erpOrderId+ " with this Status: "+status+ " and these details: "+status_description;
	        
	    	List<CreateOrderResponseItem> ordItemRespList = orderResponse.getItems();
	    	
	    	List<GenericValue> orderItems = orh.getOrderItems();
	    	
	    	for(GenericValue orderItem : orderItems)
	    	{
	    		String productId = null;
	    		
	    		for(CreateOrderResponseItem cori : ordItemRespList)
	    		{
	    			
	    			StringBuffer sbProduct = new StringBuffer();
	    			sbProduct.append(cori.getSeasonId()).append(cori.getCollectionId()).append(cori.getProductId()).append(cori.getColorId()).append(".").append(cori.getSize());
	    			
	    			productId = sbProduct.toString();
	    			
	    			BigDecimal orderRow = cori.getOrderRow();
	    			
	    			StringBuffer sbTotalExtId = new StringBuffer();
	    			sbTotalExtId.append(erpOrderId).append("/").append(orderRow);
	    		
	    			BigDecimal qtaOrdItem = new BigDecimal(cori.getQtaOrd());
	    			
	    			if(orderItem.getString("productId").equals(productId) && orderItem.getBigDecimal("quantity").compareTo(qtaOrdItem) == 0)
	    			{
	    				Debug.logWarning("*** prepare to store erpOrderId into externalId ...", MODULE);
	    				logger.logInfo("*** prepare to store erpOrderId into externalId ...");
	    				
	    				orderItem.put("externalId", sbTotalExtId.toString());
	    				try {
							orderItem.store();
							
							Debug.logWarning("*** erpOrderId saved!", MODULE);
		    				logger.logInfo("***erpOrderId saved!");
							
						} catch (GenericEntityException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
	    			
	    			}
	    			
	    		}
	    	}
	    	
	    	Debug.logWarning("*** prepare to store mpOrderId...", MODULE);
			logger.logInfo("*** prepare to store mpOrderId...");
	    	
	    	orderHeader.put("mpOrderId", erpOrderId);
	    	try {
	    		orderHeader.store();
	    		
	    		Debug.logWarning("*** mpOrderId stored! ", MODULE);
				logger.logInfo("*** mpOrderId stored! ");
	    		
			} catch (GenericEntityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        if(useCustomLogger) logger.logInfo(msg);
	        Debug.logWarning(msg, MODULE);
	        
	        if(useCustomLogger) logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
	        
	        return ServiceUtil.returnSuccess(msg);
        	
        }
	        
    }
    
}//end class
