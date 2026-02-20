/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.shipment.order;


import mpstyle.util.MpStyleShipmentUtil;
import mpstyle.util.MpStyleUtil;
import mpstyle.util.email.MpEmailServices;
import mpstyle.util.email.MpEmailUtil;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntity;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityConditionList;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.order.order.OrderReadHelper;
import org.apache.ofbiz.party.contact.ContactHelper;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.Map.Entry;



/**
 *
 * @author equake58
 */
public class MpOrderServices {
    
    public static final String module = MpOrderServices.class.getName();
    
    private final static String logfilename = "MP_EXPORD_DATA_LOG";
    private final static String LOGISTIC_PARTY_ROLE = "SHIPMENT_CLERK";
    private final static String LOGISTIC_PARTY_EMAIL_PURPOSE = "ORDER_EMAIL";
    private static String OMNI_SYSTEM_RESOURCE_ID = "mpomni";
    private static String MP_SYSTEM_RESOURCE_ID = "mpstyle";
  
    
    /**
     * Default format csv
     * @param dctx
     * @param context
     * @return 
     */
    public Map<String, Object> exportSimpleOrderDataWithStatus(DispatchContext dctx, Map<String, Object> context) {
        
        Locale locale = (Locale) context.get("locale");
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        String tenantId = delegator.getDelegatorTenantId();
        
        final String csvHeader = "ORDER_ID;ORDER_ITEM_SEQ_ID;PRODUCT_ID;QTY;ORDER_DATE;ORDER_TOTAL;ORDER_STATUS";
        
        final String DEFAULT_ORDER_STATUS = "ORDER_APPROVED";
        
        String outFileName = null;
        String orderStatus = null;
        String itemStatus = null;
        
        String outPath = (String) context.get("outPath");
        String statusId = (String) context.get("statusId");
        String logisticPartyId = (String) context.get("logisticPartyId");
        String sendTo = (String) context.get("sendTo");
        String sendFrom = (String) context.get("sendFrom");
        String sendCc = (String) context.get("sendCc");
        String subject = (String) context.get("subject");
        String fileName = (String) context.get("fileName");
        String username = (String) context.get("username");
        String password = (String) context.get("password");
        Timestamp orderFromDate = (Timestamp) context.get("orderFromDate");
        Timestamp orderThruDate = (Timestamp) context.get("orderThruDate");
        
        //Check if logisticPartyId has correct role and is associated to one or more facilites
        if(!MpStyleShipmentUtil.checkPartyShipmentRole(logisticPartyId, delegator)) {
            Debug.logError("Logistic ["+logisticPartyId+"] has not correct role. Role required SHIPMENT_CLERK.", module);
            return ServiceUtil.returnError("Logistic ["+logisticPartyId+"] has not correct role. Role required SHIPMENT_CLERK.");
        }
        
        
        //Check if this logistic party is associated to some facilities
        List<String> partyFacilities = MpStyleShipmentUtil.getPartyAssocFacilitiesWithRole(logisticPartyId, LOGISTIC_PARTY_ROLE, delegator);
        
        if(UtilValidate.isEmpty(partyFacilities)) {
            Debug.logError("Party ["+logisticPartyId+"] is not associated to any facilities with role "+LOGISTIC_PARTY_ROLE+". Cannot proceed.", module);
            return ServiceUtil.returnError("Party ["+logisticPartyId+"] is not associated to any facilities with role "+LOGISTIC_PARTY_ROLE+". Cannot proceed.");
        }
        
        int orderProcessed = 0;
    
        if(!outPath.endsWith("/")) {
            outPath = outPath + "/";
        }
        
        if(!fileName.endsWith(MpStyleUtil.CSV_EXT)) {
            fileName = tenantId + "_" + MpStyleUtil.getNowDateTimeString() +"_" + fileName + MpStyleUtil.CSV_EXT;
        }else{
            fileName = tenantId + "_" + MpStyleUtil.getNowDateTimeString() +"_" + fileName;
        }
        
        outFileName = outPath + fileName; //full path name of the file
        
        File csvFile = null;
        
        csvFile = new File(outFileName);
        FileWriter fw = null;
        
        try {
            fw = new FileWriter(csvFile);
        } catch (IOException ex) {
           Debug.logError(ex.getMessage(), module);
        }
        
        try {
            if(fw != null) {
                fw.write(csvHeader);
                fw.write(System.lineSeparator());
            }
        } catch (IOException ex) {
            Debug.logError(ex.getMessage(), module);
        }
        
        if("CREATED".equals(statusId)) {
            orderStatus = "ORDER_CREATED";
            itemStatus = "ITEM_CREATED";
        }else if("APPROVED".equals(statusId)) {
            orderStatus = "ORDER_APPROVED";
            itemStatus = "ITEM_APPROVED";
        }else if("COMPLETED".equals(statusId)) {
            orderStatus = "ORDER_COMPLETED";
            itemStatus = "ITEM_COMPLETED";
        }else if("CANCELLED".equals(statusId)) {
            orderStatus = "ORDER_CANCELLED";
            itemStatus = "ITEM_CANCELLED";
        }
        
        List<GenericValue> orderList = null;
        
        if(UtilValidate.isEmpty(orderFromDate) && UtilValidate.isEmpty(orderThruDate)) {
        
            //Retrieve orders
            EntityCondition orderStatusCond = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, orderStatus);

            try {

                orderList = delegator.findList("OrderHeader", orderStatusCond, null, UtilMisc.toList("entryDate"), null, false);

            }catch(GenericEntityException e) {
                return ServiceUtil.returnError(e.getMessage());
            }


            

            if(UtilValidate.isEmpty(orderList)) {
                String msg = "No orders found to export for the status ["+orderStatus+"]. Do nothing";
                return ServiceUtil.returnSuccess(msg);

            }
            
        }else{
            
            orderList = MpStyleUtil.getOrderDateFilterList(delegator, orderStatus, orderFromDate, orderThruDate);
            
            if(UtilValidate.isEmpty(orderList)) {
                String msg = "No orders found to export for the status ["+orderStatus+"], fromDate ["+orderFromDate+"], thruDate ["+orderThruDate+"]. Do nothing.";
                return ServiceUtil.returnSuccess(msg);

            }
            
        }
        
        
        //Loop the orders
        for(GenericValue orderHeader : orderList) {
            
            StringBuilder sb = new StringBuilder();
            
            String orderId = (String) orderHeader.get("orderId");
            BigDecimal grandTotal = (BigDecimal) orderHeader.get("grandTotal");
            Timestamp orderDate = (Timestamp) orderHeader.get("entryDate");
            
            OrderReadHelper _orh = new OrderReadHelper(orderHeader);
            
            
            List<GenericValue> orderItemList = null;

            try {
                
                EntityCondition itemCond = EntityCondition.makeCondition(EntityOperator.AND,
                                                EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId),
                                                EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, itemStatus));
                
                orderItemList = delegator.findList("OrderItem", itemCond, null, null, null, false); 
                        
            }catch(GenericEntityException e) {
                Debug.logError(e.getMessage(), module);
            }
            
            
           
            //Put in a list the order items that are shippable by the logistic party
            //List<GenericValue> shippableOrderItemsInvRes = new ArrayList<>();
            
            List<GenericValue> shippableOrderItems = new ArrayList<>();
            
            for(GenericValue orderItem : orderItemList) {
                
                List<GenericValue> orderItemShipGrpAssoc = _orh.getOrderItemShipGroupAssocs(orderItem);
           
            
                //Loop each ship group and check inventory reservation: I will store  
                //the inventory reservation record to pass to the logistic provider
                for(GenericValue shipGroupAssoc : orderItemShipGrpAssoc) {

                    String _shipGroupSeqId = (String) shipGroupAssoc.get("shipGroupSeqId");
                    String _orderId = (String) shipGroupAssoc.get("orderId");
                    String _orderItemSeqId = (String) shipGroupAssoc.get("orderItemSeqId");

                    //Get Inventory reservation facility
                    EntityCondition shpGrpInvResCond = EntityCondition.makeCondition(EntityOperator.AND,
                                                    EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, _orderId),
                                                    EntityCondition.makeCondition("orderItemSeqId", EntityOperator.EQUALS, _orderItemSeqId),
                                                    EntityCondition.makeCondition("shipGroupSeqId", EntityOperator.EQUALS, _shipGroupSeqId));

                    List<GenericValue> ordItmShipGrpInvRes = null;

                    try {
                        ordItmShipGrpInvRes = delegator.findList("OrderItemShipGrpInvRes", shpGrpInvResCond, null, null, null, false);
                    }catch(GenericEntityException gee) {
                        String msg = "Error in retrieving inventory reservation for order item ["+_orderId+"-"+_orderItemSeqId+"]. Cannot proceed.";
                        Debug.logError(gee, msg, module);
                        ServiceUtil.returnError(msg);
                    }


                    for(GenericValue oiShipGrpInvRes : ordItmShipGrpInvRes) {

                        String _inventoryItemId = (String) oiShipGrpInvRes.get("inventoryItemId");

                        GenericValue inventoryItem = null;

                        try {
                            inventoryItem = delegator.findOne("InventoryItem", UtilMisc.toMap("inventoryItemId", _inventoryItemId), false);
                        }catch(GenericEntityException gee) {
                            Debug.logError(gee.getMessage(), module);
                            return ServiceUtil.returnError("Error in retrieving inventory item for order item ["+orderItem.getString("orderId")+"-"+orderItem.getString("orderItemSeqId")+"].");
                        }

                        String invFacilityId = (String) inventoryItem.get("facilityId");

                        if(partyFacilities.contains(invFacilityId)) {

                            //for split shipment: keep the record of reservation not the whole order item
                            if(!shippableOrderItems.contains(orderItem)) {
                                shippableOrderItems.add(orderItem);
                            }

                        }

                    }

                }
        
            }
            
            if(shippableOrderItems.isEmpty()) {
                Debug.logWarning("@@@ No orders/order items shippable for logistic party id ["+logisticPartyId+"], and order id ["+ orderId +"]. Check inventory item facility or party/facility role. Quit.", module);
                continue;
                //return ServiceUtil.returnSuccess(msg);
            }
            
            
            for(GenericValue shippableItem : shippableOrderItems) {
                
                String orderItemSeqId = (String) shippableItem.get("orderItemSeqId");
                String orderItemProductId = (String) shippableItem.get("productId");
                BigDecimal qty = (BigDecimal) shippableItem.get("quantity");

                sb.append(orderId).append(";");

                sb.append(orderItemSeqId).append(";").append(orderItemProductId).append(";").append(qty.toPlainString());

                sb.append(";").append(orderDate.toString()).append(";").append(grandTotal.toPlainString()).append(";").append(orderStatus).append("\n");
                
            }
 
            //System.out.println("To append:"+sb.toString());
            
            try {
                if(fw != null) {
                    fw.write(sb.toString());
                    fw.write(System.lineSeparator());
                }
            } catch (IOException ex) {
                Debug.logError(ex.getMessage(), module);
            }
            
            sb = null;
            
            orderProcessed++;
            
            
        }
        
        try {
            if(fw != null) {
                fw.close();
            }
        } catch (IOException ex) {
            Debug.logError(ex.getMessage(), module);
        }
        
        
        Debug.logWarning("Csv file written", module);
        
        //Sending file via email
        String emailObject = tenantId + subject + " : " + orderStatus;
        boolean emailSent = MpEmailServices.sendMailWithAttachment(outFileName, fileName, sendFrom, sendTo, sendCc, null, emailObject, "text/plain", username, password, dispatcher);

        Debug.logWarning("Email sent: "+emailSent, module);
        
        return ServiceUtil.returnSuccess("Report created for orders with status [ "+orderStatus+"]. Order processed: "+ orderProcessed +". Sent via mail:"+emailSent);   
        
    }
    
    /**
     * 
     * @param dctx
     * @param context
     * @return 
     */
    public static Map<String, Object> sendMailOrderItemShipReportByFacilityReservation(DispatchContext dctx, Map<String, Object> context) {
        
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        String tenantId = delegator.getDelegatorTenantId();
        
        String statusId = (String) context.get("statusId");
        String shipmentClerkClassGroupId = (String) context.get("shipmentClerkClassificationGroupId");
        //String sendTo = (String) context.get("sendTo");
        String sendFrom = (String) context.get("sendFrom");
        String sendCc = (String) context.get("sendCc");
        String subject = (String) context.get("subject");
        //String fileName = (String) context.get("fileName");
        String username = (String) context.get("username");
        String password = (String) context.get("password");
        
        List<String> skippedOrderItems = new ArrayList<>();
        
        //Check the shipment party classification group existance
        GenericValue classificationGroup = null;
        
        try {
            classificationGroup = delegator.findOne("PartyClassificationGroup", UtilMisc.toMap("partyClassificationGroupId", shipmentClerkClassGroupId), false);
        }catch(GenericEntityException gee) {
            String msg = "Error in retrieving PartyClassificationGroup with id ["+shipmentClerkClassGroupId+"]. Quit.";
            Debug.logError(gee, msg, module);
            return ServiceUtil.returnError(msg);
        }
        
        if(classificationGroup == null) {
            return ServiceUtil.returnError("PartyClassificationGroup with id ["+shipmentClerkClassGroupId+"] does not exists. Quit.");
        }
        
        //Get all the shipment parties in the classification group
        List<GenericValue> shipmentClerkParties = MpStyleShipmentUtil.getShipmentClerkPartiesFromClassGroup(classificationGroup.getString("partyClassificationGroupId"), delegator);
        
        //do check on each party....
        if(UtilValidate.isEmpty(shipmentClerkParties)) {
            return ServiceUtil.returnError("No shipment clerk parties found. Quit.");
        }
        
        //Map of Party with list of facilities related
        Map<String, List<String>> validShipmentClerkFacilities = new HashMap<>();
        
        for(GenericValue shipmentClerk : shipmentClerkParties) {
            
            String _logisticPartyId = (String) shipmentClerk.get("partyId");
            
            //Check if logisticPartyId has correct role and is associated to one or more facilites
            if(!MpStyleShipmentUtil.checkPartyShipmentRole(_logisticPartyId, delegator)) {
                Debug.logError("Logistic ["+_logisticPartyId+"] has not correct role. Role required SHIPMENT_CLERK.", module);
                continue;
            }
            
            //Check if this logistic party is associated to some facilities
            List<String> partyFacilities = MpStyleShipmentUtil.getPartyAssocFacilitiesWithRole(_logisticPartyId, LOGISTIC_PARTY_ROLE, delegator);
        
            if(UtilValidate.isEmpty(partyFacilities)) {
                Debug.logError("Party ["+_logisticPartyId+"] is not associated to any facilities with role "+LOGISTIC_PARTY_ROLE+". Skip this party.", module);
                continue;
            }
            
            //here the party is ok
            validShipmentClerkFacilities.put(_logisticPartyId, partyFacilities);
            
        }
        
        if(UtilValidate.isEmpty(validShipmentClerkFacilities)) {
            return ServiceUtil.returnError("No valid logistic parties found. Quit.");
        }
        
        
        //Ok valid parties found. Now retrieve all reservations for order items.
        
        /* Retrieve all ITEM_APPROVED orders items. */
        
        EntityCondition approvedItemsCond = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_APPROVED");
        
        List<GenericValue> orderItemList = null;
        
        try {
            orderItemList = delegator.findList("OrderItem", approvedItemsCond, null, null, null, false);
        }catch(GenericEntityException gee) {
            Debug.logError(gee.getMessage(), module);
            return ServiceUtil.returnError(gee.getMessage());
        }
        
        if(UtilValidate.isEmpty(orderItemList)) {
            return ServiceUtil.returnError("No order items ready to be shipped found. Quit.");
        }
        
        
        //Prepare a map of item reservations for each party based on facility
        Map<String, List<GenericValue>> partyInventoryReservation = new HashMap<>();
        
        List<GenericValue> allOrderItemReservation = new ArrayList<>();
        
        //for each order item retrieve its reservations
        for(GenericValue orderItem : orderItemList) {
            
            String _orderId = (String) orderItem.get("orderId");
            String _orderItemSeqId = (String) orderItem.get("orderItemSeqId");
            
            List<GenericValue> ordItmShipGrpInvRes = null;

            try {

                //Get Inventory reservation facility
                EntityCondition shpGrpInvResCond = EntityCondition.makeCondition(EntityOperator.AND,
                                                EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, _orderId),
                                                EntityCondition.makeCondition("orderItemSeqId", EntityOperator.EQUALS, _orderItemSeqId));


                ordItmShipGrpInvRes = delegator.findList("OrderItemShipGrpInvRes", shpGrpInvResCond, null, null, null, false);
            }catch(GenericEntityException gee) {
                String msg = "Error in retrieving inventory reservation for order item ["+_orderId+"-"+_orderItemSeqId+"]. Cannot proceed.";
                Debug.logError(gee, msg, module);
                ServiceUtil.returnError(msg);
            }
            
            if(ordItmShipGrpInvRes == null || UtilValidate.isEmpty(ordItmShipGrpInvRes)) {
                //String msg = "No reservations found for order "+_orderId+" and orderItemSeqId "+ _orderItemSeqId + ". Skipping this order item.";
                String skippedOItem = "["+_orderId+"-"+_orderItemSeqId+"]";
                skippedOrderItems.add(skippedOItem);
                continue;
            }
            
            allOrderItemReservation.addAll(ordItmShipGrpInvRes);
            
        }//end loop order items
        
        if(UtilValidate.isEmpty(allOrderItemReservation)) {
                return ServiceUtil.returnError("No reservations found for any APPROVED order items. Nothing to process. Quit.");
            }
        
        //loop each party and give the reservation to the first who is related to the same facility of the inventory item
        for(Entry<String, List<String>> _entry : validShipmentClerkFacilities.entrySet()) {
            
            //Party
            String _shipClerkPartyId = _entry.getKey();
            
            //List of facility enabled for the party
            List<String> _shipClerkFacilities = (List<String>) _entry.getValue();
            
            
            //Use a list iterator because I want to delete an element from the list once given to a party
            ListIterator<GenericValue> iter = allOrderItemReservation.listIterator();
            
            
            while(iter.hasNext()) {
                
                GenericValue invItemRes = iter.next();
                
                String _invItemId = (String) invItemRes.get("inventoryItemId");
                GenericValue _invItem = null;
                
                try {
                    _invItem = invItemRes.getRelatedOne("InventoryItem", false);
                } catch (GenericEntityException ex) {
                    String msg = "Error in retrieving InventoryItem relation for inventory reservation record ["+invItemRes+"].";
                    Debug.logError(ex, msg, module);
                    return ServiceUtil.returnError(msg);
                }
                
                String _invFacilityId = (String) _invItem.get("facilityId");
                
                if(_shipClerkFacilities.contains(_invFacilityId)) {
                    
                    if(partyInventoryReservation.get(_shipClerkPartyId) == null) {
                        
                        List<GenericValue> partyReservations = new ArrayList<>();
                        partyReservations.add(invItemRes);
                        partyInventoryReservation.put(_shipClerkPartyId, partyReservations);
                        iter.remove();
                        
                    }else{
                        
                        List<GenericValue> partyReservations = partyInventoryReservation.get(_shipClerkPartyId);
                        partyReservations.add(invItemRes);
                        partyInventoryReservation.put(_shipClerkPartyId, partyReservations);
                        iter.remove();
                    }
                    
                    
                }
                
                
            }
            
        }
        
        
        //Ok now I have reservation for each party: build a mail for each one
        System.out.println("##### Reservation list for parties: \n"+UtilMisc.printMap(partyInventoryReservation));
        
        
        //System.out.println("Sending test email...");

        for(Entry<String, List<GenericValue>> _entry : partyInventoryReservation.entrySet()) {
            
            String _partyId = _entry.getKey();
            
            //Get party
            GenericValue _logisticParty = null;
            
            try {
                _logisticParty = delegator.findOne("Party", UtilMisc.toMap("partyId", _partyId), false);
            }catch(GenericEntityException gee)  {
                Debug.logError(gee, "Error in retrieving Party with id ["+_partyId+"].");
                return ServiceUtil.returnError("Error in retrieving Party with id ["+_partyId+"].");
            }
            
            
            //Retrieve contact email address from party
            String _sendTo = "";
            
            List<GenericValue> emailAddr = (List) ContactHelper.getContactMechByPurpose(_logisticParty, LOGISTIC_PARTY_EMAIL_PURPOSE, false);
            
            if(UtilValidate.isNotEmpty(emailAddr)) {
                
                emailAddr = EntityUtil.orderBy(emailAddr, UtilMisc.toList("fromDate DESC"));
                
                GenericValue emailContact = EntityUtil.getFirst(emailAddr);
                
                _sendTo = (String) emailContact.get("infoString");
                
            }else{
                Debug.logError("Email address of type ["+LOGISTIC_PARTY_EMAIL_PURPOSE+"] not found for party ["+_partyId+"]. Cannot send email. Skip it.", module);
                continue;
            }
            
            
            //Ok I have an email address..continue.
            List<GenericValue> partyReservation = (List) _entry.getValue();
            
            if(partyReservation == null || UtilValidate.isEmpty(partyReservation)) {
                Debug.logWarning("No reservations for this party ["+_partyId+"]. Skip.", module);
                continue;
            }
            
            GenericValue firstRes = EntityUtil.getFirst(partyReservation);
            String _currentOrderId = (String) firstRes.get("orderId");
            String _shipGroupSeqId = (String) firstRes.get("shipGroupSeqId");
            
            String oldOrderId = _currentOrderId;
            
            StringBuilder bodyBuilder = new StringBuilder();
            
            bodyBuilder.append(MpEmailUtil.createHtmlBodyTitle("Orders To Ship"));
            
            //Creating first order header row
            StringBuilder orderBuilder = new StringBuilder();
            OrderReadHelper _orh = new OrderReadHelper(delegator, _currentOrderId);
            GenericValue _orderHeader = _orh.getOrderHeader();

            Timestamp _orderDate = _orderHeader.getTimestamp("orderDate");

            String _orderDateString = MpStyleUtil.getMovimodaOrderDateString(_orderDate);
            String orderNumHeader = MpEmailUtil.createHtmlTextHeader(_currentOrderId+" (" + _orderDateString + ")");
            String orderEmail = _orh.getOrderEmailString();
            
            String createdBy = (String) _orderHeader.get("createdBy");
            
            GenericValue placingCustomer = _orh.getPlacingParty();
            
            String telephone = "";
            
            //Party Telecom Number
            List<GenericValue> phonePurposeList = null;
            
            if(UtilValidate.isNotEmpty(createdBy) && "anonymous".equals(createdBy)) {
                
                phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PHONE_HOME", false);
                
            }else{
                
                phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PRIMARY_PHONE", false);
            }
            
            
            if(UtilValidate.isEmpty(phonePurposeList)) {
                //Debug.logWarning("Setting NUMTEL", module);
                
                telephone = "Tel.: N/A";
                
            }else{
                
                phonePurposeList = EntityUtil.orderBy(phonePurposeList, UtilMisc.toList("lastUpdatedStamp"));
                
                GenericValue phonePurposeGV = EntityUtil.getFirst(phonePurposeList);
                
                String contactMechId = (String) phonePurposeGV.get("contactMechId");
                
                GenericValue telecomNumber = null;
                
                try {
                    telecomNumber = delegator.findOne("TelecomNumber", UtilMisc.toMap("contactMechId", contactMechId), false);
                }catch(GenericEntityException e) {
                    //Debug.logError(e.getMessage(), module);
                    return ServiceUtil.returnError(e.getMessage());
                }
                
                if(telecomNumber != null) {
                    telephone = "Tel.: " + (String) telecomNumber.get("contactNumber");
                }
                
            }
            
            orderBuilder.append(orderNumHeader);
            
            GenericValue shipAddress = _orh.getShippingAddress(_shipGroupSeqId);
            
            String shipToInfo = shipAddress.getString("toName") + "- email: " + orderEmail + " - "+telephone;
            
            String orderHtmlShipTo = MpEmailUtil.createHtmlTextLine("SHIP TO: "+shipToInfo);
            
            String address1 = (String) shipAddress.get("address1");
            String city = (String) shipAddress.get("city");
            String postalCode = (String) shipAddress.get("postalCode");
            String countryGeoId = (String) shipAddress.get("countryGeoId");
            
            String orderHtmlShipAddress = MpEmailUtil.createHtmlTextLine("SHIP ADDRESS: "+address1 + ", " + city + ", " +postalCode + ", " + countryGeoId);

            orderBuilder.append(orderHtmlShipTo);
            orderBuilder.append(orderHtmlShipAddress);
            orderBuilder.append("<ul>");
            
            //Loop the reservations
            for(GenericValue reserv : partyReservation) {
                
                _currentOrderId = (String) reserv.get("orderId");
                String _orderItemSeqId = (String) reserv.get("orderItemSeqId");
                
                //new order
                if(!_currentOrderId.equals(oldOrderId)) {
                    System.out.println("new order: "+_currentOrderId+", old order: "+oldOrderId);
                    //finalize last order items data
                    orderBuilder.append("</ul>").append("<br>");
                    bodyBuilder.append(orderBuilder.toString());
                    
                    orderBuilder = null;
                    
                    
                    //prepare for new order
                    orderBuilder = new StringBuilder();
                
                    _orh = new OrderReadHelper(delegator, _currentOrderId);

                    _orderHeader = _orh.getOrderHeader();

                    _orderDate = _orderHeader.getTimestamp("orderDate");

                    _orderDateString = MpStyleUtil.getMovimodaOrderDateString(_orderDate);

                    orderNumHeader = MpEmailUtil.createHtmlTextHeader(_currentOrderId+" (" + _orderDateString + ")");
                    
                    orderBuilder.append(orderNumHeader);
                    
                    shipAddress = _orh.getShippingAddress(_shipGroupSeqId);
                    
                    orderEmail = _orh.getOrderEmailString();
                    
                    createdBy = (String) _orderHeader.get("createdBy");
                    
                    placingCustomer = _orh.getPlacingParty();
                    
                    telephone = "";
                    
                    //Party Telecom Number
                    phonePurposeList = null;

                    if(UtilValidate.isNotEmpty(createdBy) && "anonymous".equals(createdBy)) {

                        phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PHONE_HOME", false);

                    }else{

                        phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PRIMARY_PHONE", false);
                    }


                    if(UtilValidate.isEmpty(phonePurposeList)) {
                        //Debug.logWarning("Setting NUMTEL", module);

                        telephone = "Tel.: N/A";

                    }else{

                        phonePurposeList = EntityUtil.orderBy(phonePurposeList, UtilMisc.toList("lastUpdatedStamp"));

                        GenericValue phonePurposeGV = EntityUtil.getFirst(phonePurposeList);

                        String contactMechId = (String) phonePurposeGV.get("contactMechId");

                        GenericValue telecomNumber = null;

                        try {
                            telecomNumber = delegator.findOne("TelecomNumber", UtilMisc.toMap("contactMechId", contactMechId), false);
                        }catch(GenericEntityException e) {
                            //Debug.logError(e.getMessage(), module);
                            return ServiceUtil.returnError(e.getMessage());
                        }

                        if(telecomNumber != null) {
                            telephone = "Tel.:" + (String) telecomNumber.get("contactNumber");
                        }

                    }
                    
                    shipToInfo = shipAddress.getString("toName") + "- email: " + orderEmail + " - "+telephone;
            
                    orderHtmlShipTo = MpEmailUtil.createHtmlTextLine("SHIP TO: "+shipToInfo);

                    address1 = (String) shipAddress.get("address1");
                    city = (String) shipAddress.get("city");
                    postalCode = (String) shipAddress.get("postalCode");
                    countryGeoId = (String) shipAddress.get("countryGeoId");

                    orderHtmlShipAddress = MpEmailUtil.createHtmlTextLine("SHIP ADDRESS: "+address1 + ", " + city + ", " +postalCode + ", " + countryGeoId);

                    orderBuilder.append(orderHtmlShipTo);
                    orderBuilder.append(orderHtmlShipAddress);
                    
                    //append item
                    GenericValue _orderItem = _orh.getOrderItem(_orderItemSeqId);
                    
                    String _productId = (String) _orderItem.get("productId");
                    BigDecimal qty = reserv.getBigDecimal("quantity");
                    
                    orderBuilder.append(MpEmailUtil.createHtmlListElement("SKU: " + _productId + ", QTY: " + qty.toPlainString()));

                }else{
                    //System.out.println("Same order");
                    //append item
                    GenericValue _orderItem = _orh.getOrderItem(_orderItemSeqId);
                    
                    String _productId = (String) _orderItem.get("productId");
                    BigDecimal qty = reserv.getBigDecimal("quantity");
                    
                    orderBuilder.append(MpEmailUtil.createHtmlListElement("SKU: " + _productId + ", QTY: " + qty.toPlainString()));
                    
                    
                }
                
                oldOrderId = _currentOrderId;
                
            }
            
            
            bodyBuilder.append(orderBuilder.toString());
                
            
            
            boolean mailSent = MpEmailServices.sendSimpleMail(bodyBuilder.toString(), sendFrom, _sendTo, sendCc, null, subject, "text/html", username, password, dispatcher);
             
            System.out.println("mail sent for party ["+_partyId+"]? "+mailSent);
        }
        
        String retMsg = "";
        
        if(skippedOrderItems.size() > 0) {
            retMsg = "Sent reservations to stores. There are"+ skippedOrderItems.size() +"skipped order items ["+skippedOrderItems+"].";
        }else{
            retMsg = "Sent reservations to stores.";
        }
        
        return ServiceUtil.returnSuccess(retMsg);
    }
    
    /**
     * 
     * @param dctx
     * @param context
     * @return 
     */
    public static Map<String, Object> sendEmailOrderItemsResToRetailStores(DispatchContext dctx, Map<String, Object> context) {
        
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        String tenantId = delegator.getDelegatorTenantId();
        
        String orderId = (String) context.get("orderId");
        
        boolean test = false;
                
        //user name and password for services
        String username = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "serviceUsername", delegator);
        String password = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "servicePassword", delegator);
        
        if(test) {
            
            Debug.logWarning("*** order id: "+orderId, module);
            //Debug.logWarning("*** retail classification group id: "+retailClassificationGroupId, module);
            Debug.logWarning("*** service username: "+username, module);
            Debug.logWarning("*** service password: "+password, module);
            
        }
        
        //Retrieve orderHeader and orderItems (APPROVED)
        OrderReadHelper orh = new OrderReadHelper(delegator, orderId);
        
        
        List<GenericValue> approvedItems = orh.getOrderItemsByCondition(EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_APPROVED"));
        
        if(UtilValidate.isEmpty(approvedItems)) {
            String msg = "No APPROVED items found for order ["+orderId+"]. Doing nothing.";
            Debug.logError(msg, module);
            return ServiceUtil.returnError(msg);
        }
        
        //Prepare a map of item reservations for each party based on facility
        Map<String, List<GenericValue>> partyInventoryReservation = new HashMap<>();
        //List of all the reservation for this order
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
            String msg = "No order item inventory reservations found for order ["+orderId+"]. Doing nothing.";
            Debug.logError(msg, module);
            return ServiceUtil.returnError(msg);
        }
        
        
        /*STEP 1: loop the reservations and retrieve all the shipment clerks found for each res facility */
        for(GenericValue invItemRes : allOrderItemReservation) {
            
            //String _invItemId = (String) invItemRes.get("inventoryItemId");
            GenericValue _invItem = null;

            try {
                _invItem = invItemRes.getRelatedOne("InventoryItem", false);
            } catch (GenericEntityException ex) {
                String msg = "Error in retrieving InventoryItem relation for inventory reservation record ["+invItemRes+"].";
                Debug.logError(ex, msg, module);
                return ServiceUtil.returnError(msg);
            }

            String _invFacilityId = (String) _invItem.get("facilityId");
            
            List<String> facilityShpClerkPartyList = MpStyleShipmentUtil.getFacilityAssocPartyWithRole(_invFacilityId, LOGISTIC_PARTY_ROLE, delegator);
            
            //if no shipment clerk parties are found for this reservation facility, skip it.
            if(UtilValidate.isEmpty(facilityShpClerkPartyList)) {
                String msg = "No party with role ["+LOGISTIC_PARTY_ROLE+"] found for the facility ["+_invFacilityId+"]. Skipping this reservation.";
                Debug.logError(msg, module);
                continue;
            }
            
            //Give the reservation to the first party found
            String designatadClerkParty = facilityShpClerkPartyList.get(0);
            
            List<GenericValue> partyReservationList = null;
            
            //this party has already some reservation associated to him
            if(partyInventoryReservation.get(designatadClerkParty) != null) {
                partyReservationList = partyInventoryReservation.get(designatadClerkParty);
                partyReservationList.add(invItemRes);

            }else{
                partyReservationList = new ArrayList<>();
                partyReservationList.add(invItemRes);
            }

            partyInventoryReservation.put(designatadClerkParty, partyReservationList);
                
        }
        
        /* STEP 2: build and send an email to each shipment clerk */
        for(Entry<String, List<GenericValue>> _entry : partyInventoryReservation.entrySet()) {
            
            String _partyId = _entry.getKey();
            
            //Get party
            GenericValue _logisticParty = null;
            
            try {
                _logisticParty = delegator.findOne("Party", UtilMisc.toMap("partyId", _partyId), false);
            }catch(GenericEntityException gee)  {
                Debug.logError(gee, "Error in retrieving Party with id ["+_partyId+"].");
                return ServiceUtil.returnError("Error in retrieving Party with id ["+_partyId+"].");
            }
            
            
            //Retrieve contact email address from party
            String _sendTo = "";
            
            List<GenericValue> emailAddr = (List) ContactHelper.getContactMechByPurpose(_logisticParty, LOGISTIC_PARTY_EMAIL_PURPOSE, false);
            
            if(UtilValidate.isNotEmpty(emailAddr)) {
                
                emailAddr = EntityUtil.orderBy(emailAddr, UtilMisc.toList("fromDate DESC"));
                
                GenericValue emailContact = EntityUtil.getFirst(emailAddr);
                
                _sendTo = (String) emailContact.get("infoString");
                
            }else{
                Debug.logError("Email address of type ["+LOGISTIC_PARTY_EMAIL_PURPOSE+"] not found for party ["+_partyId+"]. Cannot send email. Skip it.", module);
                continue;
            }
            
            
            //Ok I have an email address..continue.
            List<GenericValue> partyReservation = (List) _entry.getValue();
            
            if(partyReservation == null || UtilValidate.isEmpty(partyReservation)) {
                Debug.logWarning("No reservations for this party ["+_partyId+"]. Skip.", module);
                continue;
            }
            
            GenericValue firstRes = EntityUtil.getFirst(partyReservation);
            String _currentOrderId = (String) firstRes.get("orderId");
            String _shipGroupSeqId = (String) firstRes.get("shipGroupSeqId");
            
            String oldOrderId = _currentOrderId;
            
            StringBuilder bodyBuilder = new StringBuilder();
            
            bodyBuilder.append(MpEmailUtil.createHtmlBodyTitle("Orders To Ship"));
            
            //Creating first order header row
            StringBuilder orderBuilder = new StringBuilder();
            OrderReadHelper _orh = new OrderReadHelper(delegator, _currentOrderId);
            GenericValue _orderHeader = _orh.getOrderHeader();

            Timestamp _orderDate = _orderHeader.getTimestamp("orderDate");

            String _orderDateString = MpStyleUtil.getMovimodaOrderDateString(_orderDate);
            String orderNumHeader = MpEmailUtil.createHtmlTextHeader(_currentOrderId+" (" + _orderDateString + ")");
            String orderEmail = _orh.getOrderEmailString();
            
            String createdBy = (String) _orderHeader.get("createdBy");
            
            GenericValue placingCustomer = _orh.getPlacingParty();
            
            String telephone = "";
            
            //Party Telecom Number
            List<GenericValue> phonePurposeList = null;
            
            if(createdBy != null && UtilValidate.isNotEmpty(createdBy) && "anonymous".equals(createdBy)) {
                
                phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PHONE_HOME", false);
                
            }else{
                
                phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PRIMARY_PHONE", false);
            }
            
            
            if(UtilValidate.isEmpty(phonePurposeList)) {
                
                telephone = "Tel.: N/A";
                
            }else{
                
                phonePurposeList = EntityUtil.orderBy(phonePurposeList, UtilMisc.toList("lastUpdatedStamp"));
                
                GenericValue phonePurposeGV = EntityUtil.getFirst(phonePurposeList);
                
                String contactMechId = (String) phonePurposeGV.get("contactMechId");
                
                GenericValue telecomNumber = null;
                
                try {
                    telecomNumber = delegator.findOne("TelecomNumber", UtilMisc.toMap("contactMechId", contactMechId), false);
                }catch(GenericEntityException e) {
                    return ServiceUtil.returnError(e.getMessage());
                }
                
                if(telecomNumber != null) {
                    telephone = "Tel.: " + (String) telecomNumber.get("contactNumber");
                }
                
            }
            
            orderBuilder.append(orderNumHeader);
            
            GenericValue shipAddress = _orh.getShippingAddress(_shipGroupSeqId);
            
            String shipToInfo = shipAddress.getString("toName") + "- email: " + orderEmail + " - "+telephone;
            
            String orderHtmlShipTo = MpEmailUtil.createHtmlTextLine("SHIP TO: "+shipToInfo);
            
            String address1 = (String) shipAddress.get("address1");
            String city = (String) shipAddress.get("city");
            String postalCode = (String) shipAddress.get("postalCode");
            String countryGeoId = (String) shipAddress.get("countryGeoId");
            
            String orderHtmlShipAddress = MpEmailUtil.createHtmlTextLine("SHIP ADDRESS: "+address1 + ", " + city + ", " +postalCode + ", " + countryGeoId);

            orderBuilder.append(orderHtmlShipTo);
            orderBuilder.append(orderHtmlShipAddress);
            orderBuilder.append("<ul>");
            
            //Loop the reservations
            for(GenericValue reserv : partyReservation) {
                
                _currentOrderId = (String) reserv.get("orderId");
                String _orderItemSeqId = (String) reserv.get("orderItemSeqId");
                
                //new order
                if(!_currentOrderId.equals(oldOrderId)) {
                    //finalize last order items data
                    orderBuilder.append("</ul>").append("<br>");
                    bodyBuilder.append(orderBuilder.toString());
                    
                    orderBuilder = null;
                    
                    //prepare for new order
                    orderBuilder = new StringBuilder();
                
                    _orh = new OrderReadHelper(delegator, _currentOrderId);

                    _orderHeader = _orh.getOrderHeader();

                    _orderDate = _orderHeader.getTimestamp("orderDate");

                    _orderDateString = MpStyleUtil.getMovimodaOrderDateString(_orderDate);

                    orderNumHeader = MpEmailUtil.createHtmlTextHeader(_currentOrderId+" (" + _orderDateString + ")");
                    
                    orderBuilder.append(orderNumHeader);
                    
                    shipAddress = _orh.getShippingAddress(_shipGroupSeqId);
                    
                    orderEmail = _orh.getOrderEmailString();
                    
                    createdBy = (String) _orderHeader.get("createdBy");
                    
                    placingCustomer = _orh.getPlacingParty();
                    
                    telephone = "";
                    
                    //Party Telecom Number
                    phonePurposeList = null;

                    if(UtilValidate.isNotEmpty(createdBy) && "anonymous".equals(createdBy)) {

                        phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PHONE_HOME", false);

                    }else{

                        phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PRIMARY_PHONE", false);
                    }

                    if(UtilValidate.isEmpty(phonePurposeList)) {

                        telephone = "Tel.: N/A";

                    }else{

                        phonePurposeList = EntityUtil.orderBy(phonePurposeList, UtilMisc.toList("lastUpdatedStamp"));

                        GenericValue phonePurposeGV = EntityUtil.getFirst(phonePurposeList);

                        String contactMechId = (String) phonePurposeGV.get("contactMechId");

                        GenericValue telecomNumber = null;

                        try {
                            telecomNumber = delegator.findOne("TelecomNumber", UtilMisc.toMap("contactMechId", contactMechId), false);
                        }catch(GenericEntityException e) {
                            return ServiceUtil.returnError(e.getMessage());
                        }

                        if(telecomNumber != null) {
                            telephone = "Tel.:" + (String) telecomNumber.get("contactNumber");
                        }

                    }
                    
                    shipToInfo = shipAddress.getString("toName") + "- email: " + orderEmail + " - "+telephone;
            
                    orderHtmlShipTo = MpEmailUtil.createHtmlTextLine("SHIP TO: "+shipToInfo);

                    address1 = (String) shipAddress.get("address1");
                    city = (String) shipAddress.get("city");
                    postalCode = (String) shipAddress.get("postalCode");
                    countryGeoId = (String) shipAddress.get("countryGeoId");

                    orderHtmlShipAddress = MpEmailUtil.createHtmlTextLine("SHIP ADDRESS: "+address1 + ", " + city + ", " +postalCode + ", " + countryGeoId);

                    orderBuilder.append(orderHtmlShipTo);
                    orderBuilder.append(orderHtmlShipAddress);
                    
                    //append item
                    GenericValue _orderItem = _orh.getOrderItem(_orderItemSeqId);
                    
                    String _productId = (String) _orderItem.get("productId");
                    BigDecimal qty = reserv.getBigDecimal("quantity");
                    
                    orderBuilder.append(MpEmailUtil.createHtmlListElement("SKU: " + _productId + ", QTY: " + qty.toPlainString()));

                }else{
                    
                    //append item
                    GenericValue _orderItem = _orh.getOrderItem(_orderItemSeqId);
                    
                    String _productId = (String) _orderItem.get("productId");
                    BigDecimal qty = reserv.getBigDecimal("quantity");
                    
                    orderBuilder.append(MpEmailUtil.createHtmlListElement("SKU: " + _productId + ", QTY: " + qty.toPlainString()));
                    
                }
                
                oldOrderId = _currentOrderId;
                
            }
            
            bodyBuilder.append(orderBuilder.toString());
            
            /*Retrieve the email template 
            GenericValue emailTemplate = null;
            String sendFrom = "";
            String sendCc = "";
            String sendBcc = "";
            String subject = "";
            boolean sendEmail = false;
            
            try {
                
                emailTemplate = delegator.findOne("EmailTemplateSetting", UtilMisc.toMap("emailTemplateSettingId", "ORDER_INV_RES_MAIL"), false);
            }catch(GenericEntityException gee) {
                Debug.logError(gee.getMessage(), module);
            }
            
            For now just retrieve address parameters (from, cc, bcc, subject..)
            if(emailTemplate != null) {
                sendFrom = (String) emailTemplate.get("fromAddress");
                sendCc = (String) emailTemplate.get("ccAddress");
                sendBcc = (String) emailTemplate.get("bccAddress");
                subject = (String) emailTemplate.get("subject");
            }
            */
            
            HashMap<String, Object> emailCtx = new HashMap<>();
            
            HashMap<String, Object> bodyParametersMap = new HashMap<>();
            bodyParametersMap.put("partyInventoryReservationMap", partyInventoryReservation);
            
            emailCtx.put("emailTemplateSettingId", "ORDER_INV_RES_MAIL");
            emailCtx.put("bodyText", bodyBuilder.toString());
            emailCtx.put("contentType", "text/html");
            emailCtx.put("sendTo", _sendTo);
            emailCtx.put("login.username", username);
            emailCtx.put("login.password", password);
            
            Map<String, Object> emailResult = null;
            
            try {
                emailResult = dispatcher.runSync("sendMailFromTemplateSetting", emailCtx);
            }catch(GenericServiceException e) {
                Debug.logError(e.getMessage(), module);
                return ServiceUtil.returnError(e.getMessage());
            }
            
            boolean emailSent = ServiceUtil.isSuccess(emailResult);
          
        }
        
        
        return ServiceUtil.returnSuccess();
    }
    
    public Map<String, Object> findAllCreatedOrdersAndSendMail(DispatchContext ctx, Map<String, ? extends Object> context) 
	{
		Map<String,Object> result = null;
		
		Delegator delegator = ctx.getDelegator();
		LocalDispatcher dispatcher = (LocalDispatcher) ctx.getDispatcher();
		
		List<GenericValue> orderList = findAllOrdersCreated(delegator, "ORDER_CREATED");
		
		String sendFrom = EntityUtilProperties.getPropertyValue(MP_SYSTEM_RESOURCE_ID, "email.sendFrom",delegator);
		String _sendTo = EntityUtilProperties.getPropertyValue(MP_SYSTEM_RESOURCE_ID, "email.sendTo",delegator);
		String sendCc = EntityUtilProperties.getPropertyValue(MP_SYSTEM_RESOURCE_ID, "email.sendCc",delegator);
		String subject = EntityUtilProperties.getPropertyValue(MP_SYSTEM_RESOURCE_ID, "email.subjectMail",delegator);
		String username = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "email.username", delegator);
		String password = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "email.password", delegator);
		
		StringBuffer sb = new StringBuffer();
		
		sb.append("<h1>").append("Elenco ordini nello stato CREATO. Esportazione del (data_ora : ").append(UtilDateTime.nowDateString(UtilDateTime.getDateTimeFormat())).append(")").append("</h1>");
		sb.append("<h2>").append("Totale ordini in stato creato: ").append(orderList.size()).append("</h2>");
		sb.append("<ul>");
		
		for(GenericValue order : orderList) 
		{
			sb.append("<li>").append("Id ordine: ").append(order.getString("orderId")).append(" ").append(" ").append("data creazione: ").append(order.getString("entryDate")).append("</li>");
		}
		
		sb.append("</ul>");
		
		boolean mailSent = MpEmailServices.sendSimpleMail(sb.toString(), sendFrom, _sendTo, sendCc, null, subject, "text/html", username, password, dispatcher);
		
		if(mailSent)
		{	
			result = ServiceUtil.returnSuccess("Mail inviata con successo: "+mailSent);
		}else {
			result = ServiceUtil.returnError("Errore sull'invio della mail: "+mailSent);
		}
		
		return result;
	
	}
	

	private List<GenericValue> findAllOrdersCreated(Delegator delegator, String orderStatus) 
	{
		List<GenericValue> orderResultList = null;
		
		Map<String,Object> fieldValueMap = UtilMisc.toMap("statusId", orderStatus);
		
        try 
        {
        	orderResultList = delegator.findList("OrderHeader", EntityCondition.makeCondition(fieldValueMap), null, UtilMisc.toList("orderId"), null, false);
            
        }catch(GenericEntityException gee) {
            Debug.logError(gee, module);
            return null;
        }
		
		return orderResultList;
	}
    
	
    public static Map<String, Object> cancelSalesOrdersAndSendMail(DispatchContext dctx, Map<String, ? extends Object> context) 
    {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        //Locale locale = (Locale) context.get("locale");
        
        String sendFrom = EntityUtilProperties.getPropertyValue(MP_SYSTEM_RESOURCE_ID, "email.sendFrom",delegator);
		String _sendTo = EntityUtilProperties.getPropertyValue(MP_SYSTEM_RESOURCE_ID, "email.sendTo",delegator);
		String sendCc = EntityUtilProperties.getPropertyValue(MP_SYSTEM_RESOURCE_ID, "email.sendCc",delegator);
		String subject = EntityUtilProperties.getPropertyValue(MP_SYSTEM_RESOURCE_ID, "email.subject",delegator);
		String username = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "email.username", delegator);
		String password = EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "email.password", delegator);
        
        // default days to cancel
        int daysTillCancel = 30;

        List<GenericValue> ordersToCheck = null;

        // create the query expressions
        List<EntityCondition> exprs = UtilMisc.toList(
                EntityCondition.makeCondition("orderTypeId", EntityOperator.EQUALS, "SALES_ORDER"),
                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "ORDER_COMPLETED"),
                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "ORDER_CANCELLED"),
                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "ORDER_REJECTED")
       );
        
       EntityConditionList<EntityCondition> ecl = EntityCondition.makeCondition(exprs, EntityOperator.AND);

        // get the orders
        try 
        {
            ordersToCheck = delegator.findList("OrderHeader", ecl, null, UtilMisc.toList("orderDate"), null, false);
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting order headers", module);
        }

        if (UtilValidate.isEmpty(ordersToCheck)) 
        {
            Debug.logInfo("No orders to check, finished", module);
            return ServiceUtil.returnSuccess();
        }

        Timestamp nowTimestamp = UtilDateTime.nowTimestamp();
        
        Map<GenericValue,Boolean> orderCancelledMap = new HashMap<GenericValue,Boolean>();
        
        Map<String,Object> resultMail = new HashMap<String,Object>();
        
        for(GenericValue orderHeader : ordersToCheck) 
        {
            String orderId = orderHeader.getString("orderId");
            
            Debug.logWarning("********************************orderid:"+orderId, module);
            
            String orderStatus = orderHeader.getString("statusId");

            Debug.logWarning("********************************orderStatus:"+orderStatus, module);
            
            if (orderStatus.equals("ORDER_CREATED")) 
            {
                // first check for un-paid orders
                Timestamp orderDate = orderHeader.getTimestamp("entryDate");
                
                // need the store for the order
                GenericValue productStore = null;
                
                try 
                {
                    productStore = orderHeader.getRelatedOne("ProductStore", false);
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Unable to get ProductStore from OrderHeader", module);
                }

                // get the value from the store
                if (productStore != null && productStore.get("daysToCancelNonPay") != null) 
                {
                    daysTillCancel = productStore.getLong("daysToCancelNonPay").intValue();
                }

                if (daysTillCancel > 0) 
                {
                    // 0 days means do not auto-cancel
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(orderDate.getTime());
                    cal.add(Calendar.DAY_OF_YEAR, daysTillCancel);
                    Date cancelDate = cal.getTime();
                    Date nowDate = new Date();
                    
                    if (cancelDate.equals(nowDate) || nowDate.after(cancelDate)) 
                    {
                        // cancel the order item(s)
                        Map<String, Object> svcCtx = UtilMisc.<String, Object>toMap("orderId", orderId, "statusId", "ITEM_CANCELLED", "userLogin", userLogin);
                        try 
                        {
                            // TODO: looks like result is ignored here, but we should be looking for errors
                            dispatcher.runSync("changeOrderItemStatus", svcCtx);
                        } catch (GenericServiceException e) {
                            Debug.logError(e, "Problem calling change item status service : " + svcCtx, module);
                        }
                        
                        String noteMsg = "L'ordine è stato cancellato automaticamente il: "+UtilDateTime.nowDateString(UtilDateTime.getDateTimeFormat())+" poichè più vecchio di "+daysTillCancel+" giorni.";
                        boolean resultNote = createOrderNote(orderId, noteMsg, dispatcher, delegator);
                        
                        orderCancelledMap.put(orderHeader, resultNote);
                        
                    }
                
                }
                
                
            } else {
                // check for auto-cancel items
                List itemsExprs = new ArrayList();

                // create the query expressions
                itemsExprs.add(EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId));
                itemsExprs.add(EntityCondition.makeCondition(UtilMisc.toList(EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_CREATED"))));
                itemsExprs.add(EntityCondition.makeCondition("dontCancelSetUserLogin", EntityOperator.EQUALS, GenericEntity.NULL_FIELD));
                itemsExprs.add(EntityCondition.makeCondition("dontCancelSetDate", EntityOperator.EQUALS, GenericEntity.NULL_FIELD));
                itemsExprs.add(EntityCondition.makeCondition("autoCancelDate", EntityOperator.NOT_EQUAL, GenericEntity.NULL_FIELD));

                ecl = EntityCondition.makeCondition(itemsExprs);

                List<GenericValue> orderItems = null;
                
                try 
                {
                    orderItems = delegator.findList("OrderItem", ecl, null, null, null, false);
                
                } catch (GenericEntityException e) {
                    Debug.logError(e, "Problem getting order item records", module);
                }
                
                if (UtilValidate.isNotEmpty(orderItems)) 
                {
                    for(GenericValue orderItem : orderItems) 
                    {
                        String orderItemSeqId = orderItem.getString("orderItemSeqId");
                        Timestamp autoCancelDate = orderItem.getTimestamp("autoCancelDate");
                        
                        Debug.logWarning("********************************orderItemSeqId:"+orderItemSeqId, module);
                        
                        Debug.logWarning("********************************autoCancelDate:"+autoCancelDate, module);

                        if (autoCancelDate != null) 
                        {
                            if (nowTimestamp.equals(autoCancelDate) || nowTimestamp.after(autoCancelDate)) 
                            {
                                // cancel the order item
                                Map<String, Object> svcCtx = UtilMisc.<String, Object>toMap("orderId", orderId, "orderItemSeqId", orderItemSeqId, "statusId", "ITEM_CANCELLED", "userLogin", userLogin);
                                
                                try 
                                {
                                    // TODO: check service result for an error return
                                    dispatcher.runSync("changeOrderItemStatus", svcCtx);
                                } catch (GenericServiceException e) {
                                    Debug.logError(e, "Problem calling change item status service : " + svcCtx, module);
                                }
                                
                                String noteMsg = "L'ordine è stato cancellato automaticamente il: "+UtilDateTime.nowDateString(UtilDateTime.getDateTimeFormat())+" poichè più vecchio di "+daysTillCancel+" giorni.";
                                boolean resultNote = createOrderNote(orderId, noteMsg, dispatcher, delegator);
                                
                                orderCancelledMap.put(orderHeader, resultNote);
                            }
                        }
                    }
                }
            }
            
            
        }
        
        boolean mailSent = false;
        
        Debug.logWarning("********************orderCancelledMap: "+UtilMisc.printMap(orderCancelledMap), module);
        
        if(orderCancelledMap != null && !orderCancelledMap.isEmpty())
			mailSent = extracted(dispatcher, sendFrom, _sendTo, sendCc, subject, username, password, daysTillCancel,
					orderCancelledMap);
        
        if(mailSent)
    	{	
    		return ServiceUtil.returnSuccess("Mail inviata con successo: "+mailSent);
    	}else {
    		return ServiceUtil.returnError("Errore sull'invio della mail: "+mailSent);
    	}
        
    }

	private static boolean extracted(LocalDispatcher dispatcher, String sendFrom, String _sendTo, String sendCc,
			String subject, String username, String password, int daysTillCancel,
			Map<GenericValue, Boolean> orderCancelledMap) 
	{
		boolean mailSent;
		{	
        
        	StringBuffer sb = new StringBuffer();
		
        	sb.append("<h1>").append("Elenco ordini CANCELLATI").append("</h1>");
		
        	sb.append("<h2>").append("Totale ordini cancellati: ").append(orderCancelledMap.size()).append("</h2>");
		
        	sb.append("<ul>");
		
        	for(Entry<GenericValue, Boolean> entry : orderCancelledMap.entrySet())
        	{
        		
        		sb.append("<li>").append("L'ordine: ").append(entry.getKey().get("orderId")).append(" ").append(" ").append("creato il: ").append(entry.getKey().getString("entryDate")).append(" ").append("è stato cancellato automaticamente il: ").append(UtilDateTime.nowDateString(UtilDateTime.getDateTimeFormat()))
    			.append(" ").append("poichè più vecchio di: ").append(daysTillCancel).append(" ").append("giorni").append("</li>");
        		
        	}
		
        	sb.append("</ul>");
		
        	mailSent = MpEmailServices.sendSimpleMail(sb.toString(), sendFrom, _sendTo, sendCc, null, subject, "text/html", username, password, dispatcher);
        	
        }
		return mailSent;
	}
    
    private static boolean createOrderNote(String orderId, String noteMsg, LocalDispatcher dispatcher, Delegator delegator) {

        if (orderId == null || UtilValidate.isEmpty(orderId)) {
            Debug.logError("OrderId is null or empty. Cannot create order note.", module);
            return false;
        }

        if (noteMsg == null || UtilValidate.isEmpty(noteMsg)) {
            Debug.logError("Note message is null or empty. Cannot create order note.", module);
            return false;
        }

        String username = getServiceUsername(delegator);
        String password = getServicePassword(delegator);

        Map<String, Object> inMap = new HashMap<>();
        inMap.put("internalNote", "Y");
        inMap.put("orderId", orderId);
        inMap.put("note", noteMsg);
        inMap.put("login.username", username);
        inMap.put("login.password", password);

        Map<String, Object> returnMap = null;

        try {
            returnMap = dispatcher.runSync("createOrderNote", inMap);
        } catch (GenericServiceException gse) {
            Debug.logError(gse.getMessage(), module);
            return false;
        }

        return (returnMap != null && ServiceUtil.isSuccess(returnMap));
       
    }

    public static String getServiceUsername(Delegator delegator) 
    {
        return EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "serviceUsername",delegator);
    }
    
    public static String getServicePassword(Delegator delegator) 
    {
        return EntityUtilProperties.getPropertyValue(OMNI_SYSTEM_RESOURCE_ID, "servicePassword", delegator);
    }
    
    
}//end class
