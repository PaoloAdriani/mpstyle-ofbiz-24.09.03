/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.shipment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import mpstyle.log.MpStyleLogger;
import mpstyle.util.MpStyleShipmentUtil;
import mpstyle.util.MpStyleUtil;
import mpstyle.util.email.MpEmailServices;
import mpstyle.util.fixedlength.FileLunghezzaFissaWriter;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;


import com.ibm.icu.text.Transliterator;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.order.order.OrderReadHelper;
import org.apache.ofbiz.party.contact.ContactHelper;
import org.apache.ofbiz.party.party.PartyHelper;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.HashMap;


/**
 *
 * @author equake58
 */
public class MpStyleShipmentServices {
    
    public static final String module = MpStyleShipmentServices.class.getName();
    
    private final static String LOGISTIC_ROLE = "SHIPMENT_CLERK";

    private final static String logfilename = "MP_SHIPM_SRV_LOG";
    private final static String shiplabel_logfilename = "MP_SHIPM_LAB_LOG";
    
    private static String TRANSLITERATE_ID = "NFD; Any-Latin; Latin-ASCII; NFC";
    private static String NORMALIZE_ID = "NFD; [:Nonspacing Mark:] Remove; NFC";
    
    
    
    private final static String DEFAULT_SHIP_GROUP_ID = "00001";
    private final static String DEFAULT_WEIGHT = "4,000";
    private final static String DEFAULT_HEIGHT = "010";
    private final static String DEFAULT_LENGTH = "060";
    private final static String DEFAULT_DEPTH = "040";
    
    private final static String DEFAULT_FTP_HOSTNAME = "cloud2.mpstyle.it";
    private final static String DEFAULT_FTP_PASSW = "Ecomm3rc3_@bh!";
    private final static String DEFAULT_FTP_USERNAME = "Ecommerce_abh";
    private final static int DEFAULT_FTP_PORT = 21;
    
    private final static String ABH_TNT_ITA_CODE = "00040975";
    private final static String ABH_TNT_INT_CODE = "08048973";
    
    private final static String DEFAULT_RTN_CONTACT = "Gian Marco";
    private final static String DEFAULT_RTN_TONAME = "DressCode c/0 Abraham Industries";
    private final static String DEFAULT_RTN_ADDRESS = "Via dei Maniscalchi 4";
    private final static String DEFAULT_RTN_CITY = "Carpi";
    private final static String DEFAULT_RTN_POSTALCODE = "41012";
    private final static String DEFAULT_RTN_PROV = "MO";
    private final static String DEFAULT_RTN_NAZ = "ITA";
    private final static String DEFAULT_RTN_INFOEMAIL = "info@abrahamindustries.it";
    private final static String DEFAULT_RTN_TEL = "054132771";
    
    /**
     * Creation of TNT order shipping label
     * @param dctx
     * @param context
     * @return 
     */
    public static Map<String, Object> createTNTFedexOrderDocForShippingLabel(DispatchContext dctx, Map<String, Object> context) {
        
        final String method = "createTNTFedexOrderDocForShippingLabel";
        
        Locale locale = (Locale) context.get("locale");
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        String tenantId = delegator.getDelegatorTenantId();
        
        String outPath = (String) context.get("outPath");
        String logisticPartyId = (String) context.get("logisticPartyId");
        String tntFileName = (String) context.get("tntFileName");
        String tntEmailToAddress = (String) context.get("tntEmailToAddress");
        String tntEmailObject = (String) context.get("tntEmailObject");
        String username = (String) context.get("username");
        String password = (String) context.get("password");
        
        String tntEmailFromAddress = "customercare@abrahamindustries.it";
        
        
        //Test string transliteration
        Transliterator trans = Transliterator.getInstance(TRANSLITERATE_ID+"; "+NORMALIZE_ID);
        
        int orderProcessed = 0;
        
        MpStyleLogger logger = new MpStyleLogger(delegator.getDelegatorTenantId(), logfilename);
        
        logger.logInfo("******** START (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n\n");
        
        if(!outPath.endsWith("/")) {
            outPath = outPath + "/";
        }
        
        if(!tntFileName.endsWith(".txt")) {
            tntFileName = tenantId + "_" + MpStyleUtil.getNowDateTimeString() +"_" + tntFileName + MpStyleUtil.TXT_EXT;
        }else{
            tntFileName = tenantId + "_" + MpStyleUtil.getNowDateTimeString() +"_" + tntFileName;
        }
        
        logger.logInfo("******** Setting TNT output file name to [ "+tntFileName+" ] ********\n\n");
        
        //Check if logisticPartyId has correct role and is associated to one or more facilites
        if(!MpStyleShipmentUtil.checkPartyShipmentRole(logisticPartyId, delegator)) {
            Debug.logError("Logistic ["+logisticPartyId+"] has not correct role. Role required SHIPMENT_CLERK.", module);
            return ServiceUtil.returnError("Logistic ["+logisticPartyId+"] has not correct role. Role required SHIPMENT_CLERK.");
        }
        
        
        //Check if this logistic party is associated to some facilities
        List<String> partyFacilities = MpStyleShipmentUtil.getPartyAssocFacilitiesWithRole(logisticPartyId, LOGISTIC_ROLE, delegator);
        
        if(UtilValidate.isEmpty(partyFacilities)) {
            Debug.logError("Party ["+logisticPartyId+"] is not associated to any facilities with role "+LOGISTIC_ROLE+". Cannot proceed.", module);
            return ServiceUtil.returnError("Party ["+logisticPartyId+"] is not associated to any facilities with role "+LOGISTIC_ROLE+". Cannot proceed.");
        }
        
        
        //Ok, here the logistic role are ok and there are some facilities

        String RASDES = ""; //Ragione sociale destinatario
        String INDDES = ""; //Indirizzo destinatario
        String CITDES = ""; //Città destinatario
        String CAPDES = ""; //CAP destinatario
        String PRVDES = ""; //Provincia destinatario
        String NAZION = ""; //Nazione Destinatario
        String NUMDOC = ""; //Numero DDT 
        String NUMCOL = ""; //Numero colli
        String PESO = "";   //Peso
        String VOLUME = ""; //Volume in metri cubi
        String ALTEZZA = ""; //Altezza in cm
        String LUNGHEZ = ""; // Lunghezza in cm
        String PROFON = ""; //Profondità in cm
        String NOTE = "";   //Note
        String NUMTEL = ""; //NUmero di telefono
        String CASSEG = ""; //Contrassegno
        String EMAIL = ""; //E-Mail
        String TIPMER = ""; //Tipo Merce
        String NCOLLO = ""; //Numero collo
        
        //Field length constraints
        final int RASDES_MAXCHAR = 50;
        final int INDDES_MAXCHAR = 30;
        final int CITDES_MAXCHAR = 30;
        final int CAPDES_MAXCHAR = 9;
        final int PRVDES_MAXCHAR = 2;
        final int NAZION_MAXCHAR = 2;
        final int NUMDOC_MAXCHAR = 10;
        final int NUMCOL_MAXCHAR = 5;
        final int PESO_MAXCHAR = 8;
        final int VOLUME_MAXCHAR = 7;
        final int ALTEZZA_MAXCHAR = 3;
        final int LUNGHEZ_MAXCHAR = 3;
        final int PROFON_MAXCHAR = 3;
        final int NOTE_MAXCHAR = 60;
        final int NUMTEL_MAXCHAR = 16;
        final int CASSEG_MAXCHAR = 13;
        final int EMAIL_MAXCHAR = 50;
        final int TIPMER_MAXCHAR = 1;
        final int NCOLLO_MAXCHAR = 24;
        
        
        /* Retrieve first all the COMPLETED order with the flag "mpIsExportedShipLabel"
         * unset or set to "N"
         */
        /*
        EntityCondition orderCompletedCond = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ORDER_COMPLETED");
        
        EntityCondition notEexportedCond = EntityCondition.makeCondition(EntityOperator.OR,
                                            EntityCondition.makeCondition("mpIsExportedShipLabel", EntityOperator.EQUALS, null),
                                            EntityCondition.makeCondition("mpIsExportedShipLabel", EntityOperator.EQUALS, "N"));
        
        EntityCondition orderCond = EntityCondition.makeCondition(EntityOperator.AND,orderCompletedCond, notEexportedCond);
        */
        
        //Retrieve all the order not yet processed with order items with status "ITEM_COMPLETED"
        EntityCondition notEexportedCond = EntityCondition.makeCondition(EntityOperator.OR,
                                            EntityCondition.makeCondition("mpIsExportedShipLabel", EntityOperator.EQUALS, null),
                                            EntityCondition.makeCondition("mpIsExportedShipLabel", EntityOperator.EQUALS, "N"));
        
        EntityCondition itemCompletedCond = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_COMPLETED");
        
        EntityCondition orderItemCond = EntityCondition.makeCondition(EntityOperator.AND, notEexportedCond, itemCompletedCond);
        
                
        //List<GenericValue> orderList = null;
        List<GenericValue> orderItemList = null;
        
        logger.logInfo("******** Retrieving completed items (with related order) to export ********\n\n");
        
        try {
            
            //orderList = delegator.findList("OrderHeader", orderCond, null, UtilMisc.toList("entryDate"), null, false);
            orderItemList = delegator.findList("OrderItem", orderItemCond, null, UtilMisc.toList("orderId"), null, false);
            
        }catch(GenericEntityException e) {
            //Debug.logError(e.getMessage(), module);
            return ServiceUtil.returnError(e.getMessage());
        }
        
        if(UtilValidate.isEmpty(orderItemList)) {
            String msg = "No completed-not exported order items found to process. Do nothing";
            logger.logInfo("******** " + msg + " ********\n\n");
            //Debug.logWarning(msg, module);
            return ServiceUtil.returnSuccess(msg);
            
        }
        
        /* Before performing the real processing I have to check that the orders
         * contains COMPLETED order items that have a reservation in one of the facilities
         * the logistic party is associated to.
         * Put the orders items that are go to process in a separated list.
         */
        
        
        //Loop the orders and check order items reservation facilities
        
        //Put in a list the order items that are shippable by the logistic party
        List<GenericValue> shippableOrderItems = new ArrayList<>();
        
        //Get the order items related order
        List<GenericValue> orderList = new ArrayList<>();
        
        
        for(GenericValue orderItem : orderItemList) {
            
            GenericValue _relOrderHeader = null;
            
            OrderReadHelper orh = null;
            try {
                
                _relOrderHeader = orderItem.getRelatedOne("OrderHeader", true);
                
                orh = new OrderReadHelper(_relOrderHeader);
            } catch (GenericEntityException ex) {
                Debug.logError(ex.getMessage(), module);
                return ServiceUtil.returnError(ex.getMessage());
            }
            
            
            
            List<GenericValue> orderItemIssuances = orh.getOrderItemIssuances(orderItem);
            
            for(GenericValue itemIssuance : orderItemIssuances) {
                
                String _inventoryItemId = (String) itemIssuance.get("inventoryItemId");
                
                GenericValue inventoryItem = null;
                
                try {
                    inventoryItem = delegator.findOne("InventoryItem", UtilMisc.toMap("inventoryItemId", _inventoryItemId), false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee.getMessage(), module);
                    return ServiceUtil.returnError("Error in retrieving inventory item for order item ["+orderItem.getString("orderId")+"-"+orderItem.getString("orderItemSeqId")+"].");
                }
                
                String invFacilityId = (String) inventoryItem.get("facilityId");
                
                
                if(partyFacilities.contains(invFacilityId)) {
                    
                    if(!shippableOrderItems.contains(orderItem)) {
                        shippableOrderItems.add(orderItem);
                    }
                    
                    //list of all the orders to process
                    if(!orderList.contains(_relOrderHeader)) {
                        orderList.add(_relOrderHeader);
                    }
                    
                }
                
            }
                
        }
        
        
        if(shippableOrderItems.isEmpty() || orderList.isEmpty()) {
            String msg = "No orders/order items shippable for logistic party id ["+logisticPartyId+"]. Check inventory item facility or party/facility role. Quit.";
            
            logger.logWarning("******** " + msg + " ********\n\n");
            return ServiceUtil.returnSuccess(msg);
        }
            
        
        //Ok, at this point we have shippable orders/order items. GO for label creation.
        
        
        //Debug.logWarning("Creating file [" + tntFileName + "]", module);
        FileLunghezzaFissaWriter TNTOrderFile = new FileLunghezzaFissaWriter(327, outPath + tntFileName);
        
        logger.logInfo("******** Created fixed length output file ********\n\n");
        
        //Loop the orders
        for(GenericValue orderHeader : orderList) {
            
            //Debug.logWarning("\n\n --- PROCESSING ORDER "+orderHeader.get("orderId") + "---\n", module);
            
            
            //Clear all the fields for the new record
            RASDES = ""; 
            INDDES = ""; 
            CITDES = ""; 
            CAPDES = ""; 
            PRVDES = ""; 
            NAZION = ""; 
            NUMDOC = ""; 
            NUMCOL = ""; 
            PESO = "";   
            VOLUME = ""; 
            ALTEZZA = "";
            LUNGHEZ = "";
            PROFON = ""; 
            NOTE = "";   
            NUMTEL = ""; 
            CASSEG = ""; 
            EMAIL = "";
            TIPMER = ""; 
            NCOLLO = "";
            
            
            //Create a new record
            TNTOrderFile.addNewRecord();
            
            String orderId = orderHeader.getString("orderId");
            
            logger.logInfo("******** PROCESSING ORDER "+orderId+ "********\n");
            
            OrderReadHelper orh = new OrderReadHelper(orderHeader);
            
            String createdBy = (String) orderHeader.get("createdBy");
           
            logger.logInfo("******** ...reading shipping address data... ********\n");
            
            //2 - Indirizzo
            GenericValue shippingAddress = orh.getShippingAddress(DEFAULT_SHIP_GROUP_ID);
            
 
            //1 - Ragione Sociale
            GenericValue placingCustomer = orh.getPlacingParty();
            
            String orderToName = "";
            
            orderToName = (String) shippingAddress.get("toName");
            
            if(UtilValidate.isNotEmpty(orderToName)) {
                RASDES = trans.transliterate(orderToName); //test
            }else{
                //RASDES = PartyHelper.getPartyName(placingCustomer);
                RASDES = trans.transliterate(PartyHelper.getPartyName(placingCustomer));
            }
            
            if(RASDES.length() > RASDES_MAXCHAR) {
                RASDES = RASDES.substring(0, RASDES_MAXCHAR);
            }
            
            //Debug.logWarning("Setting RASDES", module);
            TNTOrderFile.setCampo(RASDES, 1);
            
            INDDES = (String) shippingAddress.get("address1");
            
            INDDES = trans.transliterate(INDDES);
            
            if(INDDES.length() > INDDES_MAXCHAR) {
                INDDES = INDDES.substring(0, INDDES_MAXCHAR);
            }
            
            //Debug.logWarning("Setting INDDES", module);
            
            TNTOrderFile.setCampo(INDDES, 51);
            
            //3 - Città
            CITDES = (String) shippingAddress.get("city");
            
            CITDES = trans.transliterate(CITDES);
            
            if(CITDES.length() > CITDES_MAXCHAR) {
                CITDES = CITDES.substring(0, CITDES_MAXCHAR);
            }
            
            //Debug.logWarning("Setting CITDES", module);
            TNTOrderFile.setCampo(CITDES, 81);
            
            //4 - CAP
            CAPDES = (String) shippingAddress.get("postalCode");
            
            if(CAPDES.length() > CAPDES_MAXCHAR) {
                CAPDES = CAPDES.substring(0, CAPDES_MAXCHAR);
            }
            
            //Debug.logWarning("Setting CAPDES", module);
            TNTOrderFile.setCampo(CAPDES, 111);
            
            //5 - Provincia
            String stateProvinceGeoId = (String) shippingAddress.get("stateProvinceGeoId");
            
            if(stateProvinceGeoId == null || "_NA_".equals(stateProvinceGeoId)) {
                PRVDES = "";
            }else if(stateProvinceGeoId.contains("-")) {
                PRVDES = stateProvinceGeoId.substring(stateProvinceGeoId.indexOf("-")+1);
            }
            
            //Debug.logWarning("Setting PRVDES", module);
            TNTOrderFile.setCampo(PRVDES, 120);
            
            //6 - Nazione
            String ISO3_COUNTRY = (String) shippingAddress.get("countryGeoId");
            
            try {
                GenericValue country = delegator.findOne("Geo", UtilMisc.toMap("geoId", ISO3_COUNTRY), false);
                
                NAZION = (String) country.get("geoCode");
                
            }catch(GenericEntityException e) {
                //Debug.logError(e.getMessage(), module);
                return ServiceUtil.returnError(e.getMessage());
            }
            
            //Debug.logWarning("Setting NAZION", module);
            TNTOrderFile.setCampo(NAZION, 122);
            
            //7 - Numero DDT
            NUMDOC = orderId;
            //Debug.logWarning("Setting NUMDOC", module);
            TNTOrderFile.setCampo(NUMDOC, 124);
            
            logger.logInfo("******** ...setting package data... ********\n");
            
            //8 - Numero colli (sempre 1)
            NUMCOL = "1";
            //Debug.logWarning("Setting NUMCOL", module);
            TNTOrderFile.setCampo(NUMCOL, 134);
            
            //9 - Peso
            PESO = DEFAULT_WEIGHT;
            //Debug.logWarning("Setting PESO", module);
            TNTOrderFile.setCampo(PESO, 139);
            
            //10 - Volume (blank)
            //Debug.logWarning("Setting VOLUME", module);
            TNTOrderFile.setCampo(VOLUME, 147);
            
            //11 - Altezza
            ALTEZZA = DEFAULT_HEIGHT;
            //Debug.logWarning("Setting ALTEZZA", module);
            
            TNTOrderFile.setCampo(ALTEZZA, 154);
            
            //12 - LUNGHEZZA
            LUNGHEZ = DEFAULT_LENGTH;
            //Debug.logWarning("Setting LUNGHEZ", module);
            TNTOrderFile.setCampo(LUNGHEZ, 157);
            
            //13 - Profondità
            PROFON = DEFAULT_DEPTH;
            //Debug.logWarning("Setting PROFON", module);
            TNTOrderFile.setCampo(PROFON, 160);
            
            //14 - Note
            //Debug.logWarning("Setting NOTE", module);
            
            if(NOTE.length() > NOTE_MAXCHAR) {
                NOTE = NOTE.substring(0, NOTE_MAXCHAR);
            }
            
            NOTE = trans.transliterate(NOTE);
            
            TNTOrderFile.setCampo(NOTE, 163);
            
            logger.logInfo("******** ...reading telephone data... ********\n");
            
            //15 - Telefono
            List<GenericValue> phonePurposeList = null;
            
            if(createdBy != null && UtilValidate.isNotEmpty(createdBy) && "anonymous".equals(createdBy)) {
                
                phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PHONE_HOME", false);
                
            }else{
                
                phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PRIMARY_PHONE", false);
            }
            
            
            if(UtilValidate.isEmpty(phonePurposeList)) {
                //Debug.logWarning("Setting NUMTEL", module);
                
                TNTOrderFile.setCampo(NUMTEL, 223);
                
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
                    NUMTEL = (String) telecomNumber.get("contactNumber");
                }
                
                //Debug.logWarning("Setting NUMTEL", module);
                
                if(NUMTEL.length() > NUMTEL_MAXCHAR) {
                    NUMTEL = NUMTEL.substring(0, NUMTEL_MAXCHAR);
                }
                
                TNTOrderFile.setCampo(NUMTEL, 223);
                
            }
            
             logger.logInfo("******** ...reading email and set package number... ********\n");
            
            //16 -Contrassegno (blank)
            //Debug.logWarning("Setting CASSEG", module);
            TNTOrderFile.setCampo(CASSEG, 239);
            
            //17 - EMAIL
            EMAIL = orh.getOrderEmailString();
            ////Debug.logWarning("Setting EMAIL", module);
            
            if(EMAIL.length() > EMAIL_MAXCHAR) {
                EMAIL = EMAIL.substring(0, EMAIL_MAXCHAR);
            }
            
            TNTOrderFile.setCampo(EMAIL, 252);
            
            //18 - Tipo Merce
            TIPMER = "C";
            
            ////Debug.logWarning("Setting TIPMER", module);
            TNTOrderFile.setCampo(TIPMER, 302);
            
            //19 - Identificativo collo
            NCOLLO = orderId + System.currentTimeMillis();
            ////Debug.logWarning("Setting NCOLLO", module);
            
            if(NCOLLO.length() > NCOLLO_MAXCHAR) {
                NCOLLO = NCOLLO.substring(0, NCOLLO_MAXCHAR);
            }
            
            TNTOrderFile.setCampo(NCOLLO, 303);

            
            //Write the record
            TNTOrderFile.writeRecord();
            
            logger.logInfo("******** ORDER "+orderId+ " record written to file ********\n");
            
            ////Debug.logWarning("\n\n ---END  PROCESSING ORDER "+orderHeader.get("orderId") + "---\n", module);
            
            //update the flag on orderHeader and order items
            List<GenericValue> toUpdateList = new ArrayList<>();
            
            try {
                orderHeader.set("mpIsExportedShipLabel", "Y");
                
                toUpdateList.add(orderHeader);
            
                for(GenericValue orderItem : shippableOrderItems) {
                    String _oitemOrderId = (String) orderItem.get("orderId");
                    //add the order item in the update list
                    if(_oitemOrderId.equals(orderId)) {
                        orderItem.set("mpIsExportedShipLabel", "Y");
                        toUpdateList.add(orderItem);
                    }
                }
                
                
                //delegator.store(orderHeader);
                delegator.storeAll(toUpdateList);
                
            }catch(GenericEntityException e) {
                Debug.logError(e.getMessage(), module);
                logger.logError("Error in updating [mpIsExportedShipLabel] flag for order/order items "+orderId+" ********\n");
                return ServiceUtil.returnError("Error in updating [mpIsExportedShipLabel] flag for order/order items "+orderId);
            }
            
            logger.logInfo("******** ORDER "+orderId+ " PROCESSING COMPLETED: updated [mpIsExportedShipLabel] set to Y ********\n");
            
            orderProcessed++;
            
            
            
        } //end order looping
        
        
        TNTOrderFile.closeFile();
    
        boolean emailSent = MpEmailServices.sendMailWithAttachment(outPath + tntFileName, tntFileName, tntEmailFromAddress, tntEmailToAddress, null, null, tntEmailObject, "text/plain", username, password, dispatcher);
       
         logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + "Processed: "+orderProcessed+" ********\n");
        
        return ServiceUtil.returnSuccess("Successfuly exported "+orderProcessed+" orders. Email sent: "+emailSent);
        
    };
    
    /**
     * Creation of TNT return label
     * @param dctx
     * @param context
     * @return 
     */
    public Map<String, Object> createTNTFedexOrderReturnLabel(DispatchContext dctx, Map<String, Object> context) {
        
        final String method = "createTNTFedexOrderReturnLabel";
        
        final String csvHeader = "CODICE_ABRAHAM;DESTINATARIO;INDIRIZZO;CAP;COMUNE;PROV;NAZ;CONTATTO;TEL;EMAIL;PESO;"
                               +  "NUMERO_COLLI;SERVIZIO_TNT;TIPO_MERCE;FERMO_DEPOSITO;N_DDT;NOTE/ISTRUZIONI;DESCRIZIONE_MERCE;"
                               + "MITTENTE_RITIRO;INDIRIZZO_RITIRO;LOC_RITIRO;PROV_RITIRO;CAP_RITIRO;CONTATTO;TEL_RITIRO;EMAIL_RITIRO;"
                               + "IMP_C/ASS;COMM_C/ASS;IMPO_ASS.;VALUTA_ASS;TIPO_PORTO;";
        
        Locale locale = (Locale) context.get("locale");
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        String tenantId = delegator.getDelegatorTenantId();
        
        String outPath = (String) context.get("outPath");
        String tntFileName = (String) context.get("tntFileName");
        String tntEmailToAddress = (String) context.get("tntEmailToAddress");
        String tntEmailCcnAddress = (String) context.get("tntEmailCcnAddress");
        String tntEmailFromAddress = (String) context.get("tntEmailFromAddress");
        String tntEmailObject = (String) context.get("tntEmailObject");
        List<String> orderIdList = (List) context.get("orderIdList");
        String inputOrderId = (String) context.get("orderId");
        String inputReturnId = (String) context.get("returnId");
        String addresseeCompanyPartyId = (String) context.get("addresseeCompanyPartyId");
        String username = (String) context.get("username");
        String password = (String) context.get("password");
        
        MpStyleLogger logger = new MpStyleLogger(delegator.getDelegatorTenantId(), logfilename);
        
        logger.logInfo("******** START (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n\n");
        
        
        //Before everything test if required parameters are set, otherwise quit.
        if(UtilValidate.isEmpty(inputOrderId) && UtilValidate.isEmpty(inputReturnId) && UtilValidate.isEmpty(orderIdList)) {
            logger.logInfo("******** None of the parameters orderId, returnId, orderIdList have been inputed. Cannot create return data. Quit. ********\n\n");
            return ServiceUtil.returnError("None of the parameters orderId, returnId, orderIdList have been inputed. Cannot create return data. Quit.");

        }
        
        Transliterator trans = Transliterator.getInstance(TRANSLITERATE_ID+"; "+NORMALIZE_ID);
        
        int orderProcessed = 0;
        
        
        /* CSV FILEDS */
        String CODICE_ABRAHAM = "";
        String DESTINATARIO = "";
        String INDIRIZZO = "";
        String CAP = "";
        String COMUNE = "";
        String PROV = "";
        String NAZ = "";
        String CONTATTO = "";
        String TEL = "";
        String EMAIL = "";
        String PESO = "";
        String NUMERO_COLLI = "";
        String SERVIZIO_TNT = "";
        String TIPO_MERCE = "";
        String FERMO_DEPOSITO = "";
        String N_DDT = "";
        String NOTE = "";
        String DESCRIZIONE_MERCE = "";
        String MITTENTE_RITIRO = "";
        String INDIRIZZO_RITIRO = "";
        String LOC_RITIRO = "";
        String PROV_RITIRO = "";
        String CAP_RITIRO = "";
        String CONTATTO_RITIRO = "";
        String TEL_RITIRO = "";
        String EMAIL_RITIRO = "";
        String IMP_C_ASS = "";
        String COMM_C_ASS = "";
        String IMPO_ASS = "";
        String VALUTA_ASS = "";
        String TIPO_PORTO = "";
 
        
        
        if(!outPath.endsWith("/")) {
            outPath = outPath + "/";
        }
        
        if(!tntFileName.endsWith(MpStyleUtil.CSV_EXT)) {
            tntFileName = tenantId + "_" + MpStyleUtil.getNowDateTimeString() +"_" + tntFileName + MpStyleUtil.CSV_EXT;
        }else{
            tntFileName = tenantId + "_" + MpStyleUtil.getNowDateTimeString() +"_" + tntFileName;
        }
        
        logger.logInfo("******** Setting TNT output file name to [ "+tntFileName+" ] ********\n\n");
        
        String outFileName = outPath + tntFileName; //full path name of the file
        
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
        
        
        //Create return data based on returnId
        if(UtilValidate.isNotEmpty(inputReturnId)) {
            
            logger.logWarning("******** Processing return data for returnId ["+inputReturnId+"] ********\n\n");
            
            GenericValue returnHeader = null;
            
            try {
                returnHeader = delegator.findOne("ReturnHeader", UtilMisc.toMap("returnId", inputReturnId), false);
                
            }catch(GenericEntityException gee) {
                Debug.logError(gee.getMessage(), module);
                return ServiceUtil.returnError(gee.getMessage());
            }
            
            //Get return items
            List<GenericValue> returnItemList = null;
            
            
            try {
                returnItemList = delegator.findList("ReturnItem", EntityCondition.makeCondition("returnId", EntityOperator.EQUALS, inputReturnId), null, null, null, false);
            }catch(GenericEntityException gee) {
                Debug.logError(gee.getMessage(), module);
                return ServiceUtil.returnError(gee.getMessage());
            }
            
            //Quit if for some reason return item list is empty: maybe the cause is an error in return creation
            if(UtilValidate.isEmpty(returnItemList)) {
                logger.logError("**** Return Item list is empty for return id ["+inputReturnId+"]. Check for errors in return creation. Quit. ****");
                return ServiceUtil.returnError("Return Item list is empty for return id ["+inputReturnId+"]. Check for errors in return creation. Quit.");
            }
            
            
            
            Map<String, String> returnItemOrderMap = new HashMap<>();
            
            //Retrieve orders from item list: if there are return items into different orders, then create a label for each order
            for(GenericValue returnItem : returnItemList) {
                
                String _itemOrderId = (String) returnItem.get("orderId");
                
                GenericValue _relatedOrder = null;
                
                try {
                    _relatedOrder = delegator.findOne("OrderHeader", UtilMisc.toMap("orderId", _itemOrderId), false);
                }catch(GenericEntityException gee) {
                    Debug.logError(gee.getMessage(), module);
                    return ServiceUtil.returnError(gee.getMessage());
                }
                
                OrderReadHelper _orh = new OrderReadHelper(_relatedOrder);
            
                String _orderEmail = _orh.getOrderEmailString();

                returnItemOrderMap.putIfAbsent(_itemOrderId, _orderEmail);
                
            }//end return item loop
            
            
            if(returnItemOrderMap.size() > 0) {
                
                for(Map.Entry<String, String> entry : returnItemOrderMap.entrySet()) {
                    
                    StringBuilder sb = new StringBuilder();
                    
                    String _orderId = entry.getKey();
                    
                    GenericValue _orderHeader = null;
                    
                    try {
                        _orderHeader = delegator.findOne("OrderHeader", UtilMisc.toMap("orderId", _orderId), false);
                    }catch(GenericEntityException gee) {
                        Debug.logError(gee.getMessage(), module);
                        return ServiceUtil.returnError(gee.getMessage());
                    }
                     
                    logger.logInfo("******** Reading data for order [" + _orderId + "] ********\n\n");
                    
                    OrderReadHelper orh = new OrderReadHelper(_orderHeader);
                    
                    //Pick-up address: use the originCOntactMechId; if not set use the order shipping address
                    GenericValue pickUpAddress = null;
                    
                    String originContactMechId = (String) returnHeader.get("originContactMechId");
                    
                    if(UtilValidate.isNotEmpty(originContactMechId)) {
                        
                        try {
                            
                            pickUpAddress = delegator.findOne("PostalAddress", UtilMisc.toMap("contactMechId", originContactMechId), false);
                            
                        }catch(GenericEntityException gee) {
                            Debug.logError(gee.getMessage(), module);
                            return ServiceUtil.returnError(gee.getMessage());
                        }
                        
                    }else{
                        pickUpAddress = orh.getShippingAddress(DEFAULT_SHIP_GROUP_ID);
                    }
                    

                    //Check the country of the address to correctly set the Abraham TNT code to use
                    String countryGeoId = (String) pickUpAddress.get("countryGeoId");

                    if("ITA".equals(countryGeoId)) {
                        CODICE_ABRAHAM = ABH_TNT_ITA_CODE;
                    }else{
                        CODICE_ABRAHAM = ABH_TNT_INT_CODE;
                    }

                    sb.append(CODICE_ABRAHAM).append(";");
                    
                    if(UtilValidate.isNotEmpty(addresseeCompanyPartyId)) {
            
                        logger.logInfo("******** Retrieving addressee data from company partyId " + addresseeCompanyPartyId + " ********\n\n");

                        GenericValue addresseeCompanyParty = null;

                        try {
                            addresseeCompanyParty = delegator.findOne("Party", UtilMisc.toMap("partyId", addresseeCompanyPartyId), false);
                        }catch(GenericEntityException e) {
                            Debug.logError(e.getMessage(), module);
                            return ServiceUtil.returnError(e.getMessage());
                        }

                        List<GenericValue> shipOrigPurposeList = (List) ContactHelper.getContactMechByPurpose(addresseeCompanyParty, "SHIP_ORIG_LOCATION", false);

                        if(UtilValidate.isNotEmpty(shipOrigPurposeList)) {

                            shipOrigPurposeList = EntityUtil.orderBy(shipOrigPurposeList, UtilMisc.toList("lastUpdatedStamp"));

                            GenericValue shipOriginAddressPurposeGV = EntityUtil.getFirst(shipOrigPurposeList);

                            String contactMechId = (String) shipOriginAddressPurposeGV.get("contactMechId");

                            GenericValue companyShipOriginPostalAddress = null;



                            try {
                                companyShipOriginPostalAddress = delegator.findOne("PostalAddress", UtilMisc.toMap("contactMechId", contactMechId), false);
                            }catch(GenericEntityException e) {
                                Debug.logError(e.getMessage(), module);
                                return ServiceUtil.returnError(e.getMessage());
                            }


                            DESTINATARIO = (String) companyShipOriginPostalAddress.get("toName");

                            DESTINATARIO = MpStyleUtil.removeCharFromString(DESTINATARIO, ";");

                            if(  (companyShipOriginPostalAddress.get("attnName") != null)  ) {
                                CONTATTO = (String) companyShipOriginPostalAddress.get("attnName");
                            }else{
                                CONTATTO = DEFAULT_RTN_CONTACT;
                            }

                            CONTATTO = MpStyleUtil.removeCharFromString(CONTATTO, ";");

                            INDIRIZZO = (String) companyShipOriginPostalAddress.get("address1");

                            INDIRIZZO = MpStyleUtil.removeCharFromString(INDIRIZZO, ";");

                            CAP = (String) companyShipOriginPostalAddress.get("postalCode");

                            COMUNE = (String) companyShipOriginPostalAddress.get("city");

                            COMUNE = MpStyleUtil.removeCharFromString(COMUNE, ";");

                            String stateProvinceGeoId = (String) companyShipOriginPostalAddress.get("stateProvinceGeoId");

                            if(stateProvinceGeoId == null || "_NA_".equals(stateProvinceGeoId)) {
                                PROV = "";
                            }else if(stateProvinceGeoId.contains("-")) {
                                PROV = stateProvinceGeoId.substring(stateProvinceGeoId.indexOf("-")+1);
                            }

                            NAZ = (String) companyShipOriginPostalAddress.get("countryGeoId");



                        }else{
                            //use default address data
                            DESTINATARIO = DEFAULT_RTN_TONAME;
                            INDIRIZZO = DEFAULT_RTN_ADDRESS;
                            CAP = DEFAULT_RTN_POSTALCODE;
                            COMUNE = DEFAULT_RTN_CITY;
                            PROV = DEFAULT_RTN_PROV;
                            NAZ = DEFAULT_RTN_NAZ;
                            CONTATTO = DEFAULT_RTN_CONTACT;
                            //TEL = DEFAULT_RTN_TEL;



                        }

                        //Check addressee info email
                        List<GenericValue> infoEmailPurposeList = (List) ContactHelper.getContactMechByPurpose(addresseeCompanyParty, "OTHER_EMAIL", false);

                        if(UtilValidate.isNotEmpty(infoEmailPurposeList)) {

                            infoEmailPurposeList = EntityUtil.orderBy(infoEmailPurposeList, UtilMisc.toList("lastUpdatedStamp"));

                            GenericValue infoEmailPurposeGV = EntityUtil.getFirst(infoEmailPurposeList);

                            String infoEmailContactMechId = (String) infoEmailPurposeGV.get("contactMechId");

                            GenericValue companyInfoEmailAddress = null;

                            try {
                                companyInfoEmailAddress = delegator.findOne("ContactMech", UtilMisc.toMap("contactMechId", infoEmailContactMechId), false);
                            }catch(GenericEntityException e) {
                                Debug.logError(e.getMessage(), module);
                                return ServiceUtil.returnError(e.getMessage());
                            }

                            EMAIL = (String) companyInfoEmailAddress.get("infoString");

                        }else{

                            EMAIL = DEFAULT_RTN_INFOEMAIL;

                        }

                        //Check addressee phone number
                        List<GenericValue> companyPrimaryPhonePurposeList = (List) ContactHelper.getContactMechByPurpose(addresseeCompanyParty, "PRIMARY_PHONE", false);

                        if(UtilValidate.isNotEmpty(companyPrimaryPhonePurposeList)) {

                            companyPrimaryPhonePurposeList = EntityUtil.orderBy(companyPrimaryPhonePurposeList, UtilMisc.toList("lastUpdatedStamp"));

                            GenericValue companyPrimaryPhonePurposeGV = EntityUtil.getFirst(companyPrimaryPhonePurposeList);

                            String primaryPhoneContactMechId = (String) companyPrimaryPhonePurposeGV.get("contactMechId");

                            GenericValue companyPrimaryPhoneNumber = null;

                            try {
                                companyPrimaryPhoneNumber = delegator.findOne("TelecomNumber", UtilMisc.toMap("contactMechId", primaryPhoneContactMechId), false);
                            }catch(GenericEntityException e) {
                                Debug.logError(e.getMessage(), module);
                                return ServiceUtil.returnError(e.getMessage());
                            }

                            TEL = (String) companyPrimaryPhoneNumber.get("contactNumber");

                        }else{

                            TEL = DEFAULT_RTN_TEL;

                        }



                    }else{
                            //default data
                            logger.logInfo("******** addresseeCompanyPartyId is null. Using addressee default data. ********\n\n");

                            DESTINATARIO = DEFAULT_RTN_TONAME;
                            INDIRIZZO = DEFAULT_RTN_ADDRESS;
                            CAP = DEFAULT_RTN_POSTALCODE;
                            COMUNE = DEFAULT_RTN_CITY;
                            PROV = DEFAULT_RTN_PROV;
                            NAZ = DEFAULT_RTN_NAZ;
                            CONTATTO = DEFAULT_RTN_CONTACT;
                            TEL = DEFAULT_RTN_TEL;
                            EMAIL = DEFAULT_RTN_INFOEMAIL;
                    }

                    sb.append(DESTINATARIO).append(";").append(INDIRIZZO).append(";").append(CAP).append(";").append(COMUNE);
                    sb.append(";").append(PROV).append(";").append(NAZ).append(";").append(CONTATTO).append(";").append(TEL).append(";").append(EMAIL).append(";");
                    
                    PESO = "4,000";

                    sb.append(PESO).append(";");

                    NUMERO_COLLI = "1";

                    sb.append(NUMERO_COLLI).append(";");

                    SERVIZIO_TNT = "NC";

                    sb.append(SERVIZIO_TNT).append(";");

                    TIPO_MERCE = "C";

                    sb.append(TIPO_MERCE).append(";");

                    FERMO_DEPOSITO = "0";

                    sb.append(FERMO_DEPOSITO).append(";");

                    N_DDT = inputReturnId;

                    sb.append(N_DDT).append(";");

                    sb.append(NOTE).append(";");

                    DESCRIZIONE_MERCE = "Abbigliamento";

                    sb.append(DESCRIZIONE_MERCE).append(";");      
                    
                    //Dati mittente ritiro
                    MITTENTE_RITIRO = (String) pickUpAddress.get("toName");

                    MITTENTE_RITIRO = MpStyleUtil.removeCharFromString(MITTENTE_RITIRO, ";");

                    MITTENTE_RITIRO = trans.transliterate(MITTENTE_RITIRO);

                    sb.append(MITTENTE_RITIRO).append(";");


                    INDIRIZZO_RITIRO = (String) pickUpAddress.get("address1");

                    INDIRIZZO_RITIRO = MpStyleUtil.removeCharFromString(INDIRIZZO_RITIRO, ";");

                    INDIRIZZO_RITIRO = trans.transliterate(INDIRIZZO_RITIRO);

                    sb.append(INDIRIZZO_RITIRO).append(";");

                    LOC_RITIRO = (String) pickUpAddress.get("city");

                    LOC_RITIRO = MpStyleUtil.removeCharFromString(LOC_RITIRO, ";");

                    LOC_RITIRO = trans.transliterate(LOC_RITIRO);

                    sb.append(LOC_RITIRO).append(";");

                    String stateProvinceGeoId = (String) pickUpAddress.get("stateProvinceGeoId");

                    if(stateProvinceGeoId == null || "_NA_".equals(stateProvinceGeoId)) {
                        PROV_RITIRO = "";
                    }else if(stateProvinceGeoId.contains("-")) {
                        PROV_RITIRO = stateProvinceGeoId.substring(stateProvinceGeoId.indexOf("-")+1);
                    }

                    sb.append(PROV_RITIRO).append(";");

                    CAP_RITIRO = (String) pickUpAddress.get("postalCode");

                    CAP_RITIRO = MpStyleUtil.removeCharFromString(CAP_RITIRO, ";");

                    sb.append(CAP_RITIRO).append(";");

                    CONTATTO_RITIRO = (String) pickUpAddress.get("toName");

                    CONTATTO_RITIRO = MpStyleUtil.removeCharFromString(CONTATTO_RITIRO, ";");

                    CONTATTO_RITIRO = trans.transliterate(CONTATTO_RITIRO);

                    sb.append(CONTATTO_RITIRO).append(";");
                    
                    GenericValue placingCustomer = orh.getPlacingParty();

                    List<GenericValue> phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PRIMARY_PHONE", false);

                    if(UtilValidate.isEmpty(phonePurposeList)) {

                        TEL_RITIRO = "";

                    }else{

                        phonePurposeList = EntityUtil.orderBy(phonePurposeList, UtilMisc.toList("lastUpdatedStamp DESC"));

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
                            TEL_RITIRO = (String) telecomNumber.get("contactNumber");
                        }

                        //Debug.logWarning("Setting NUMTEL", module);



                    }

                    TEL_RITIRO = MpStyleUtil.removeCharFromString(TEL_RITIRO, ";");

                    sb.append(TEL_RITIRO).append(";");

                    EMAIL_RITIRO = entry.getValue(); 

                    EMAIL_RITIRO = MpStyleUtil.removeCharFromString(EMAIL_RITIRO, ";");

                    sb.append(EMAIL_RITIRO).append(";");
                    
                    IMP_C_ASS = "0";

                    sb.append(IMP_C_ASS).append(";");

                    COMM_C_ASS = "0";

                    sb.append(COMM_C_ASS).append(";");

                    IMPO_ASS = "0";

                    sb.append(IMPO_ASS).append(";");

                    VALUTA_ASS = "EUR";

                    sb.append(VALUTA_ASS).append(";");

                    TIPO_PORTO = "S";

                    sb.append(TIPO_PORTO).append("\n");
                    
                    try {
                        if(fw != null) {
                            fw.write(sb.toString());
                            fw.write(System.lineSeparator());
                        }
                    } catch (IOException ex) {
                        Debug.logError(ex.getMessage(), module);
                    }
                    
                    
                    logger.logInfo("******** End reading data for return [" + inputReturnId + "]/order "+_orderId+ ". Writing data to file ********\n");
                    
                    sb = null;
        
                    orderProcessed++;
                    
                } //end entry loop
                
            }//end returnItemOrderMap.size() > 0
            
            //close the file
            try {
                if(fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
                Debug.logError(ex.getMessage(), module);
            }
        
            Debug.logWarning("Csv file written", module);

         
        ///////////// END RETURN ID PROCESSING BRANCH ///////////////////     
            
        ///////////// PROCESSING ORDER ID LIST : NOT IMPLEMENTED YET ////////////////
        } else if(UtilValidate.isNotEmpty(orderIdList)) {
            
            
            //TODO: WORK ON ALL COMPLETED NON-TNT PROCESSED ORDERS
            logger.logWarning("******** No orderId nor returnId inputed and order loop not implemented yet. Do nothing. ********\n\n");

            return ServiceUtil.returnSuccess("No order id/return id inputed and order loop not implemented yet. Do nothing.");
            
         
        /////////////// PROCESSING RETURN DATA BY ORDER ID /////////////// 
        }else if(UtilValidate.isNotEmpty(inputOrderId)) {
            
                
            
        GenericValue orderHeader = null; 
        StringBuilder sb = new StringBuilder();


        try {

            orderHeader = delegator.findOne("OrderHeader", UtilMisc.toMap("orderId", inputOrderId), false);

        }catch(GenericEntityException e) {
            //Debug.logError(e.getMessage(), module);
            return ServiceUtil.returnError(e.getMessage());
        }
        

        if(!"ORDER_COMPLETED".equals((String) orderHeader.get("statusId"))) {
            String msg = "Order ["+inputOrderId+"] is not in COMPLETED status. Cannot create return label.";
            logger.logInfo("******** " + msg + " ********\n\n");
            Debug.logWarning(msg, module);
            return ServiceUtil.returnError(msg);

        }
        
        logger.logInfo("******** Reading order data ********\n\n");

        OrderReadHelper orh = new OrderReadHelper(orderHeader);

        //Pick-up address
        GenericValue shippingAddress = orh.getShippingAddress(DEFAULT_SHIP_GROUP_ID);

        //Check the country of the address to correctly set the Abraham TNT code to use
        String countryGeoId = (String) shippingAddress.get("countryGeoId");

        if("ITA".equals(countryGeoId)) {
            CODICE_ABRAHAM = ABH_TNT_ITA_CODE;
        }else{
            CODICE_ABRAHAM = ABH_TNT_INT_CODE;
        }

        sb.append(CODICE_ABRAHAM).append(";");

        if(UtilValidate.isNotEmpty(addresseeCompanyPartyId)) {
            
            logger.logInfo("******** Retrieving addressee data from company partyId " + addresseeCompanyPartyId + " ********\n\n");

            GenericValue addresseeCompanyParty = null;

            try {
                addresseeCompanyParty = delegator.findOne("Party", UtilMisc.toMap("partyId", addresseeCompanyPartyId), false);
            }catch(GenericEntityException e) {
                Debug.logError(e.getMessage(), module);
                return ServiceUtil.returnError(e.getMessage());
            }

            List<GenericValue> shipOrigPurposeList = (List) ContactHelper.getContactMechByPurpose(addresseeCompanyParty, "SHIP_ORIG_LOCATION", false);

            if(UtilValidate.isNotEmpty(shipOrigPurposeList)) {

                shipOrigPurposeList = EntityUtil.orderBy(shipOrigPurposeList, UtilMisc.toList("lastUpdatedStamp"));

                GenericValue shipOriginAddressPurposeGV = EntityUtil.getFirst(shipOrigPurposeList);

                String contactMechId = (String) shipOriginAddressPurposeGV.get("contactMechId");

                GenericValue companyShipOriginPostalAddress = null;



                try {
                    companyShipOriginPostalAddress = delegator.findOne("PostalAddress", UtilMisc.toMap("contactMechId", contactMechId), false);
                }catch(GenericEntityException e) {
                    Debug.logError(e.getMessage(), module);
                    return ServiceUtil.returnError(e.getMessage());
                }


                DESTINATARIO = (String) companyShipOriginPostalAddress.get("toName");

                DESTINATARIO = MpStyleUtil.removeCharFromString(DESTINATARIO, ";");

                if(  (companyShipOriginPostalAddress.get("attnName") != null)  ) {
                    CONTATTO = (String) companyShipOriginPostalAddress.get("attnName");
                }else{
                    CONTATTO = DEFAULT_RTN_CONTACT;
                }

                CONTATTO = MpStyleUtil.removeCharFromString(CONTATTO, ";");

                INDIRIZZO = (String) companyShipOriginPostalAddress.get("address1");

                INDIRIZZO = MpStyleUtil.removeCharFromString(INDIRIZZO, ";");

                CAP = (String) companyShipOriginPostalAddress.get("postalCode");

                COMUNE = (String) companyShipOriginPostalAddress.get("city");

                COMUNE = MpStyleUtil.removeCharFromString(COMUNE, ";");

                String stateProvinceGeoId = (String) companyShipOriginPostalAddress.get("stateProvinceGeoId");

                if(stateProvinceGeoId == null || "_NA_".equals(stateProvinceGeoId)) {
                    PROV = "";
                }else if(stateProvinceGeoId.contains("-")) {
                    PROV = stateProvinceGeoId.substring(stateProvinceGeoId.indexOf("-")+1);
                }

                NAZ = (String) companyShipOriginPostalAddress.get("countryGeoId");



            }else{
                //use default address data
                DESTINATARIO = DEFAULT_RTN_TONAME;
                INDIRIZZO = DEFAULT_RTN_ADDRESS;
                CAP = DEFAULT_RTN_POSTALCODE;
                COMUNE = DEFAULT_RTN_CITY;
                PROV = DEFAULT_RTN_PROV;
                NAZ = DEFAULT_RTN_NAZ;
                CONTATTO = DEFAULT_RTN_CONTACT;
                //TEL = DEFAULT_RTN_TEL;



            }

            //Check addressee info email
            List<GenericValue> infoEmailPurposeList = (List) ContactHelper.getContactMechByPurpose(addresseeCompanyParty, "OTHER_EMAIL", false);

            if(UtilValidate.isNotEmpty(infoEmailPurposeList)) {

                infoEmailPurposeList = EntityUtil.orderBy(infoEmailPurposeList, UtilMisc.toList("lastUpdatedStamp"));

                GenericValue infoEmailPurposeGV = EntityUtil.getFirst(infoEmailPurposeList);

                String infoEmailContactMechId = (String) infoEmailPurposeGV.get("contactMechId");

                GenericValue companyInfoEmailAddress = null;

                try {
                    companyInfoEmailAddress = delegator.findOne("ContactMech", UtilMisc.toMap("contactMechId", infoEmailContactMechId), false);
                }catch(GenericEntityException e) {
                    Debug.logError(e.getMessage(), module);
                    return ServiceUtil.returnError(e.getMessage());
                }

                EMAIL = (String) companyInfoEmailAddress.get("infoString");

            }else{

                EMAIL = DEFAULT_RTN_INFOEMAIL;

            }

            //Check addressee phone number
            List<GenericValue> companyPrimaryPhonePurposeList = (List) ContactHelper.getContactMechByPurpose(addresseeCompanyParty, "PRIMARY_PHONE", false);

            if(UtilValidate.isNotEmpty(companyPrimaryPhonePurposeList)) {

                companyPrimaryPhonePurposeList = EntityUtil.orderBy(companyPrimaryPhonePurposeList, UtilMisc.toList("lastUpdatedStamp DESC"));

                GenericValue companyPrimaryPhonePurposeGV = EntityUtil.getFirst(companyPrimaryPhonePurposeList);

                String primaryPhoneContactMechId = (String) companyPrimaryPhonePurposeGV.get("contactMechId");

                GenericValue companyPrimaryPhoneNumber = null;

                try {
                    companyPrimaryPhoneNumber = delegator.findOne("TelecomNumber", UtilMisc.toMap("contactMechId", primaryPhoneContactMechId), false);
                }catch(GenericEntityException e) {
                    Debug.logError(e.getMessage(), module);
                    return ServiceUtil.returnError(e.getMessage());
                }

                TEL = (String) companyPrimaryPhoneNumber.get("contactNumber");

            }else{

                TEL = DEFAULT_RTN_TEL;

            }



        }else{
                //default data
                logger.logInfo("******** addresseeCompanyPartyId is null. Using addressee default data. ********\n\n");

                DESTINATARIO = DEFAULT_RTN_TONAME;
                INDIRIZZO = DEFAULT_RTN_ADDRESS;
                CAP = DEFAULT_RTN_POSTALCODE;
                COMUNE = DEFAULT_RTN_CITY;
                PROV = DEFAULT_RTN_PROV;
                NAZ = DEFAULT_RTN_NAZ;
                CONTATTO = DEFAULT_RTN_CONTACT;
                TEL = DEFAULT_RTN_TEL;
                EMAIL = DEFAULT_RTN_INFOEMAIL;
        }

        sb.append(DESTINATARIO).append(";").append(INDIRIZZO).append(";").append(CAP).append(";").append(COMUNE);
        sb.append(";").append(PROV).append(";").append(NAZ).append(";").append(CONTATTO).append(";").append(TEL).append(";").append(EMAIL).append(";");

        PESO = "4,000";

        sb.append(PESO).append(";");

        NUMERO_COLLI = "1";

        sb.append(NUMERO_COLLI).append(";");

        SERVIZIO_TNT = "NC";

        sb.append(SERVIZIO_TNT).append(";");

        TIPO_MERCE = "C";

        sb.append(TIPO_MERCE).append(";");

        FERMO_DEPOSITO = "0";

        sb.append(FERMO_DEPOSITO).append(";");

        N_DDT = inputOrderId;

        sb.append(N_DDT).append(";");

        sb.append(NOTE).append(";");

        DESCRIZIONE_MERCE = "Abbigliamento";

        sb.append(DESCRIZIONE_MERCE).append(";");

        //Dati mittente ritiro
        MITTENTE_RITIRO = (String) shippingAddress.get("toName");

        MITTENTE_RITIRO = MpStyleUtil.removeCharFromString(MITTENTE_RITIRO, ";");

        MITTENTE_RITIRO = trans.transliterate(MITTENTE_RITIRO);

        sb.append(MITTENTE_RITIRO).append(";");


        INDIRIZZO_RITIRO = (String) shippingAddress.get("address1");

        INDIRIZZO_RITIRO = MpStyleUtil.removeCharFromString(INDIRIZZO_RITIRO, ";");

        INDIRIZZO_RITIRO = trans.transliterate(INDIRIZZO_RITIRO);

        sb.append(INDIRIZZO_RITIRO).append(";");

        LOC_RITIRO = (String) shippingAddress.get("city");

        LOC_RITIRO = MpStyleUtil.removeCharFromString(LOC_RITIRO, ";");

        LOC_RITIRO = trans.transliterate(LOC_RITIRO);

        sb.append(LOC_RITIRO).append(";");

        String stateProvinceGeoId = (String) shippingAddress.get("stateProvinceGeoId");

        if(stateProvinceGeoId == null || "_NA_".equals(stateProvinceGeoId)) {
            PROV_RITIRO = "";
        }else if(stateProvinceGeoId.contains("-")) {
            PROV_RITIRO = stateProvinceGeoId.substring(stateProvinceGeoId.indexOf("-")+1);
        }

        sb.append(PROV_RITIRO).append(";");

        CAP_RITIRO = (String) shippingAddress.get("postalCode");

        CAP_RITIRO = MpStyleUtil.removeCharFromString(CAP_RITIRO, ";");

        sb.append(CAP_RITIRO).append(";");

        CONTATTO_RITIRO = (String) shippingAddress.get("toName");

        CONTATTO_RITIRO = MpStyleUtil.removeCharFromString(CONTATTO_RITIRO, ";");

        CONTATTO_RITIRO = trans.transliterate(CONTATTO_RITIRO);

        sb.append(CONTATTO_RITIRO).append(";");

        GenericValue placingCustomer = orh.getPlacingParty();

        List<GenericValue> phonePurposeList = (List) ContactHelper.getContactMechByPurpose(placingCustomer, "PRIMARY_PHONE", false);

        if(UtilValidate.isEmpty(phonePurposeList)) {

            TEL_RITIRO = "";

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
                TEL_RITIRO = (String) telecomNumber.get("contactNumber");
            }

            //Debug.logWarning("Setting NUMTEL", module);



        }

        TEL_RITIRO = MpStyleUtil.removeCharFromString(TEL_RITIRO, ";");

        sb.append(TEL_RITIRO).append(";");

        EMAIL_RITIRO = orh.getOrderEmailString();

        EMAIL_RITIRO = MpStyleUtil.removeCharFromString(EMAIL_RITIRO, ";");

        sb.append(EMAIL_RITIRO).append(";");

        IMP_C_ASS = "0";

        sb.append(IMP_C_ASS).append(";");

        COMM_C_ASS = "0";

        sb.append(COMM_C_ASS).append(";");

        IMPO_ASS = "0";

        sb.append(IMPO_ASS).append(";");

        VALUTA_ASS = "EUR";

        sb.append(VALUTA_ASS).append(";");

        TIPO_PORTO = "S";

        sb.append(TIPO_PORTO).append("\n");

        try {
            if(fw != null) {
                fw.write(sb.toString());
                fw.write(System.lineSeparator());
            }
        } catch (IOException ex) {
            Debug.logError(ex.getMessage(), module);
        }

        logger.logInfo("******** ORDER "+inputOrderId+ " data written to file ********\n");
        sb = null;
        
        orderProcessed++;
          
        //close the file
        try {
            if(fw != null) {
                fw.close();
            }
        } catch (IOException ex) {
            Debug.logError(ex.getMessage(), module);
        }
        
        Debug.logWarning("Csv file written", module);
        
    }
        
        //Sending file via email if orders have been processed
        boolean emailSent = false;
        
        if(orderProcessed > 0) {
        
            emailSent = MpEmailServices.sendMailWithAttachment(outFileName, tntFileName, tntEmailFromAddress, tntEmailToAddress, null, tntEmailCcnAddress, tntEmailObject, "text/plain", username, password, dispatcher);

            Debug.logWarning("Email sent: "+emailSent, module);
        
        }
        
        logger.logInfo("******** END (" + method + ") - Time: " + MpStyleUtil.getNowDateTimeString() + "Processed: "+orderProcessed+" ********\n");
        
        
        return ServiceUtil.returnSuccess("Successfully requested return label for "+orderProcessed+ "orders. Email sent: "+emailSent);
        
    }
    
    
    
    /**
     * Create a ship label for each order completed 
     * @param dctx
     * @param context
     * @return 
     */
    public Map<String, Object> createStandardOrderShipLabel(DispatchContext dctx, Map<String, Object> context) {
        
        final String method = "createStandardOrderShipLabel";
        
        Locale locale = (Locale) context.get("locale");
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        String tenantId = delegator.getDelegatorTenantId();
        
        String base_label_filename = "SHIPLAB";
        ArrayList<String> toSendFtpFileList = null;
        
        String baseOutPath = (String) context.get("outPath");
        String ownerCompanyPartyId = (String) context.get("ownerCompanyPartyId");
        String inputOrderId = (String) context.get("orderId");
        
        if(!baseOutPath.endsWith("/")) {
            baseOutPath = baseOutPath + "/";
        }
        
        MpStyleLogger logger = new MpStyleLogger(delegator.getDelegatorTenantId(), shiplabel_logfilename);
        
        /* Retrieve first all the COMPLETED order with the flag "mpIsExportedShipLabel"
         * unset or set to "N"
         */
        List<GenericValue> orderList = null;
        
        if(UtilValidate.isEmpty(inputOrderId)) {
        
            EntityCondition orderCompletedCond = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ORDER_COMPLETED");

            logger.logInfo("******** Retrieving completed-exported order for labeling ********\n\n");

            try {

                orderList = delegator.findList("OrderHeader", orderCompletedCond, null, UtilMisc.toList("entryDate"), null, false);

            }catch(GenericEntityException e) {
                //Debug.logError(e.getMessage(), module);
                return ServiceUtil.returnError(e.getMessage());
            }

            if(UtilValidate.isEmpty(orderList)) {
                String msg = "No completed-exported orders found to process. Do nothing";
                logger.logInfo("******** " + msg + " ********\n\n");
                //Debug.logWarning(msg, module);
                return ServiceUtil.returnSuccess(msg);

            }
            
        }else{
            
           
            
            GenericValue orderGV = null;
            
            try {
                orderGV = delegator.findOne("OrderHeader", UtilMisc.toMap("orderId", inputOrderId), false);
            }catch(GenericEntityException e) {
                Debug.logError(e.getMessage(), module);
                return ServiceUtil.returnError(e.getMessage());
            }
            
            if(orderGV != null) {
                String orderStatus = (String) orderGV.get("statusId");

                if(!"ORDER_COMPLETED".equals(orderStatus)) {
                    Debug.logError("Order "+inputOrderId+" is not COMPLETED. Do not create ship label for it.", module);
                    return ServiceUtil.returnError("Order "+inputOrderId+" is not COMPLETED. Do not create ship label for it.");
                }
                
                 orderList = new ArrayList<>();
                 
                 orderList.add(orderGV);
                
                
            }else{
                Debug.logError("Order ["+inputOrderId+"] not found.", module);
                return ServiceUtil.returnError("Order ["+inputOrderId+"] not found.");
            }
            
        }
        
        //Get owner company party data
        GenericValue ownerParty = null;
        
        try {
            ownerParty = delegator.findOne("Party", UtilMisc.toMap("partyId", ownerCompanyPartyId), false);
        }catch(GenericEntityException e) {
            Debug.logError(e.getMessage(), module);
            return ServiceUtil.returnError(e.getMessage());
        }
      
        
        List<GenericValue> companyAddressPurposeList = (List) ContactHelper.getContactMechByPurpose(ownerParty, "PRIMARY_LOCATION", false);
        
        GenericValue companyPostalAddress = null;
        
        if(UtilValidate.isNotEmpty(companyAddressPurposeList)) {
            
            companyAddressPurposeList = EntityUtil.orderBy(companyAddressPurposeList, UtilMisc.toList("lastUpdatedStamp"));
                
            GenericValue primaryAddressPurposeGV = EntityUtil.getFirst(companyAddressPurposeList);
            
            String contactMechId = (String) primaryAddressPurposeGV.get("contactMechId");
            
            companyPostalAddress = null;
            
            try {
                    companyPostalAddress = delegator.findOne("PostalAddress", UtilMisc.toMap("contactMechId", contactMechId), false);
                }catch(GenericEntityException e) {
                    //Debug.logError(e.getMessage(), module);
                    return ServiceUtil.returnError(e.getMessage());
                }
            
         
            
        }
                
        
        int processedOrders = 0;
        
        toSendFtpFileList = new ArrayList<>();

        //Loop the orders
        for(GenericValue orderHeader : orderList) {
            
            File labelFile = null;
            FileWriter fw = null; 
            StringBuilder sb = new StringBuilder();
            String labelFilename = "";
            String outPath = "";
            
            String companyLabelData = getShipLabelCompanyData(companyPostalAddress, ownerParty);
            
            sb.append(companyLabelData);
            
            String shipToName = "";
            String shipToAddress = "";
            String shipToCity = "";
            String shipToPostalCode = "";
            String shipToCountry = "";
            String shipToProvince = "";
            
            String orderId = (String) orderHeader.get("orderId");
            
            labelFilename = base_label_filename + "_" + orderId + MpStyleUtil.TXT_EXT;
            
            outPath = baseOutPath + labelFilename;
            
            toSendFtpFileList.add(outPath);
            
            try {
                
            
                labelFile = new File(outPath);
            
                fw = new FileWriter(labelFile);
                
            }catch(IOException e) {
                Debug.logError(e.getMessage(), module);
                return ServiceUtil.returnError(e.getMessage());
            }
            
            OrderReadHelper orh = new OrderReadHelper(orderHeader);
            
            // 1 - shipToName
            
            GenericValue placingCustomer = orh.getPlacingParty();
            
            shipToName = PartyHelper.getPartyName(placingCustomer); 
            
            //2 - shipToAddress/city/postalCode/Country/province
            GenericValue shippingAddress = orh.getShippingAddress(DEFAULT_SHIP_GROUP_ID);
            
            shipToAddress = (String) shippingAddress.get("address1");
            
            shipToCity = (String) shippingAddress.get("city");
            
            shipToPostalCode = (String) shippingAddress.get("postalCode");
            
            String countryISO3 = (String) shippingAddress.get("countryGeoId");
            
            GenericValue countryGV = null;
            
            try {
                countryGV = delegator.findOne("Geo", UtilMisc.toMap("geoId", countryISO3), false);
            }catch(GenericEntityException e) {
                Debug.logError(e.getMessage(), module);
                return ServiceUtil.returnError(e.getMessage());
            }
            
            if(countryGV != null) {
                shipToCountry = (String) countryGV.get("geoName");
            }else{
                shipToCountry = countryISO3;
            }
            
            String provinceISOCode = (String) shippingAddress.get("stateProvinceGeoId");
            
            if(provinceISOCode == null || "_NA_".equals(provinceISOCode)) {
                shipToProvince = "";
            }else if(provinceISOCode.contains("-")) {
                shipToProvince = provinceISOCode.substring(provinceISOCode.indexOf("-")+1);
            }
            
            
            //Writing zebra data
            sb.append("^FO700,700^A0R,18 ^FDReceiver").append("^FS");
            
            sb.append("^FO600,700 ^A0R,70 ^FD").append(shipToName).append("^FS");
            
            sb.append("^FO400,700 ^FB870,2,,L, ^A0R,70 ^FD").append(shipToAddress).append("^FS");
            
            sb.append("^FO200,700 ^FB870,2,,L,^A0R,70 ^FD").append(shipToCity);
            
            if(UtilValidate.isNotEmpty(shipToProvince)) {
                
                sb.append("(").append(shipToProvince).append("), ");
                
            }else{
                sb.append(", ");
            }
            
            sb.append(shipToPostalCode).append("^FS");
            
            sb.append("^FO50,700 ^A0R,70 ^FD").append(shipToCountry).append("^FS");
            
            sb.append("^XZ");
            
            
            try {
                fw.write(sb.toString());
            } catch (IOException ex) {
                Debug.logError(ex.getMessage(), module);
            }
            
            try {
                fw.close();
            } catch (IOException ex) {
                Debug.logError(ex.getMessage(), module);
            }
            
            processedOrders++;
           
        } //end order loop
        
        
        //Send via FTP all the label files created
        boolean allSentViaFtp = true;
        FTPClient ftpClient = new FTPClient();
        
        try {
            
            ftpClient.connect(DEFAULT_FTP_HOSTNAME, DEFAULT_FTP_PORT);
            ftpClient.login(DEFAULT_FTP_USERNAME, DEFAULT_FTP_PASSW);
            ftpClient.enterLocalPassiveMode();
            
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            
            for(String toSendFilePath : toSendFtpFileList) {
                
                
                File firstLocalFile = new File(toSendFilePath);
                
                String[] remoteFileNameSplit = toSendFilePath.split("/");
                
                int splitLength = remoteFileNameSplit.length;
                
                String remoteFile = remoteFileNameSplit[splitLength-1];
                
                Debug.logWarning("Store to remote file: "+remoteFile, module);
                
                if(remoteFile == null) {
                    Debug.logError("Cannot create remote file for path ["+toSendFilePath+"]. File name is null. Skipping this and continue.", module);
                    continue;
                    
                }
                
                InputStream inputStream = new FileInputStream(firstLocalFile);

                Debug.logWarning("---- Uploading file ["+toSendFilePath+"]...", module);
                
                boolean done = ftpClient.storeFile(remoteFile, inputStream);
                inputStream.close();
                
                if (done) {
                    Debug.logInfo("File [" + remoteFile + "] is uploaded successfully.", module);
                }else{
                    Debug.logError("File ["+remoteFile+"] not uploaded.", module);
                    allSentViaFtp = false;
                }
                
            }
            
            
        }catch(IOException ex) {
            Debug.logError("Errore nella connessione ftp per l'invio etichette: "+ex.getMessage(), module);
            allSentViaFtp = false;
        }
        
        
        return ServiceUtil.returnSuccess("Generated "+processedOrders+" ship labels. Sent via ftp: "+allSentViaFtp);
        
    }
    
   
    
    
    private String getShipLabelCompanyData(GenericValue companyPostalAddress, GenericValue ownerParty) {
        
        StringBuilder sb = new StringBuilder();
        
        String onwerCompanyName = "";
        onwerCompanyName = PartyHelper.getPartyName(ownerParty);
        String ownerCompanyAddress = "";
        String ownerCompanyCity = "";
        String ownerCompanyPostalCode = "";
        String ownerCompanyCountry = "";
        String ownerCompanyProvince = "";
        
        if(companyPostalAddress != null) {
        
            ownerCompanyAddress = (String) companyPostalAddress.get("address1");
            ownerCompanyCity = (String) companyPostalAddress.get("city");
            ownerCompanyPostalCode = (String) companyPostalAddress.get("postalCode");
            ownerCompanyCountry = (String) companyPostalAddress.get("countryGeoId");
            ownerCompanyProvince = (String) companyPostalAddress.get("stateProvinceGeoId");
            
        }
            
        if(ownerCompanyProvince == null || "_NA_".equals(ownerCompanyProvince)) {
            ownerCompanyProvince = "";
        }else if(ownerCompanyProvince.contains("-")) {
            ownerCompanyProvince = ownerCompanyProvince.substring(ownerCompanyProvince.indexOf("-")+1);
        }
        
        sb.append("^XA").append("^FO500,60^GB235,570,3^FS").append("^FO700,65^A0R,18 ^FDSender").append("^FS");
        
        sb.append("^FO650,65 ^A0R,30 ^FD").append(onwerCompanyName).append("^FS");
        
        sb.append("^FO600,65 ^A0R,30 ^FD").append(ownerCompanyAddress).append("^FS");
        
        sb.append("^FO550,65 ^A0R,30 ^FD").append(ownerCompanyCity).append(",").append(ownerCompanyPostalCode).append(",").append(ownerCompanyCountry).append("^FS");
        
        return sb.toString();
        
    };
    
} //end class
