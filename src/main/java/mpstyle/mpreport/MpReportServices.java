/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.mpreport;

import mpstyle.util.email.MpEmailServices;
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
import org.apache.ofbiz.product.category.CategoryContentWrapper;
import org.apache.ofbiz.product.product.ProductContentWrapper;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Map;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 *
 * @author equake58
 */
public class MpReportServices {

    public static final String module = MpReportServices.class.getName();

    private final static String SYSTEM_RESOURCE_ID = "mpreport";
    private static final String DEFAULT_SHIP_GROUP_ID = "00001";
    private final static String SALES_REPORT_FILENAME = "SalesReport";
    private final static String ORDER_REPORT_FILENAME = "OrderReport";
    private final static String CUSTOMER_ORDER_REPORT_FILENAME = "CustomerOrderReport";

    private final static String REPORT_FILE_EXT = ".csv";

    private final static String SALES_REPORT_CSV_HEADER = "stag;linea;art;col;tg;descrizione;categoria;nr_ordine;data_ordine;iso_nazione_spedizione;deposito_pren;deposito_sped(data_sped);numero_fattura;y/m_fatt;art-col-tg-descr;"
            + "status_riga;single_full_price;qta_ord;qta_sped;sconto;full_price;tot_scontato;promocode_saldi;tot_pag_arrot;tot_pag;"
            + "tot_ordine;nr_fattura;data_creazione_fattura;iso_paese_spedizione;motiv_reso;nr_reso;y/m_reso;chiusura_reso;numero pezzi resi;Gateway Rimborso;Note;";

    private final static String ORDER_REPORT_CSV_HEADER = "Y/M Ord;Data;Data Ordine;Data Spedizione;Sped +X gg;Data Vendita;Anno V;Mese V;"
            + "Data Ordine CMS;Ordine #;Stato Ordine;Fattura a;Negozio;Tot. Ordine;Pagamento;pcs;Spese Trasp;"
            + "Tot. Venduto Merce;Sconti;Destinatario;Email;Localita;Prov;Note;"
            + "Reso #;Data Richiesta Reso;Reso da;Stato;Tipo Rimborso;Metodo;Data Ricezione;Tot. Rimborsato;"
            + "Scadenza Promocode;Mese check F.;Y/M Reso;Data Chiusura;pcs resi;Gateway Rimborso;Rif. Ordine #;Note;Data Registr. Cliente";

    private final static String CUSTOMER_ORDER_REPORT_CSV_HEADER = "Data Registrazione;Indirizzo Email;Nome;Cognome;Localita;Provincia;Nazione;Ordine #;"
            + "Data Ordine;Importo Ordine;Codice Sconto";

    private final static String STD_DECIMAL_CHAR = ".";
    private final static String EXC_DECIMAL_CHAR = ",";

    //private final static String MAIL_FROM_ADDRESS = "customercare@abrahamindustries.it";
    /**
     * Service that creates a Sales Report csv file. Will be considered orders
     * in COMPLETED status only.
     *
     * @param dctx
     * @param context
     * @return
     */
    public Map<String, Object> createSalesReport(DispatchContext dctx, Map<String, Object> context) {

        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();

        int yyyy = -1, mm = -1, dd = -1, h = -1, m = -1, s = -1;

        String outFilePath = null;
        File outputFile = null;
        FileWriter writer = null;

        //String outDirPath = (String) context.get("outDirPath");
        String outDirPath = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "sales.report.outdir", null, delegator);
        String username = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.username", null, delegator);
        String password = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.password", null, delegator);
        String mailFromAddress = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.mail.fromAddress", null, delegator);

        Timestamp reportFromDate = (Timestamp) context.get("reportFromDate");
        Timestamp reportThruDate = (Timestamp) context.get("reportThruDate");
        String mailToAddress = (String) context.get("mailToAddress");
        String mailCcAddress = (String) context.get("mailCcAddress");

        Locale locale = (Locale) context.get("locale");

        String tenantId = delegator.getDelegatorTenantId();

        //set up calendar
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        yyyy = calendar.get(Calendar.YEAR);
        mm = calendar.get(Calendar.MONTH) + 1;
        dd = calendar.get(Calendar.DAY_OF_MONTH);
        h = calendar.get(Calendar.HOUR_OF_DAY);
        m = calendar.get(Calendar.MINUTE);
        s = calendar.get(Calendar.SECOND);

        String outfilename = SALES_REPORT_FILENAME + "_" + tenantId + "_" + yyyy
                + mm + dd + "_" + h + m + s + REPORT_FILE_EXT;

        //System.out.println("Sales Report outDir:" + outDirPath);
        //System.out.println("reportFromDate:" + reportFromDate);
        //System.out.println("reportThruDate:" + reportThruDate);

        if (outDirPath.endsWith("/")) {

            outFilePath = outDirPath + outfilename;

        } else {

            outFilePath = outDirPath + "/" + outfilename;

        }

        //Create the file
        outputFile = new File(outFilePath);

        try {
            writer = new FileWriter(outputFile);
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }

        if (!outputFile.exists()) {
            Debug.logError("Output file " + outFilePath + "not created. Abort.", module);
            return ServiceUtil.returnFailure("Output file " + outFilePath + "not created. Abort.");
        }

        //write the csv file header
        try {
            writer.append(SALES_REPORT_CSV_HEADER);
            writer.append(System.lineSeparator());
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }

        List<GenericValue> orderList = new ArrayList<>();
        List<GenericValue> orderCompletedList = null;
        List<GenericValue> orderApprovedList = null;

        orderCompletedList = MpReportUtil.getOrderDateFilterList(delegator, "ORDER_COMPLETED", reportFromDate, reportThruDate);
        orderApprovedList = MpReportUtil.getOrderDateFilterList(delegator, "ORDER_APPROVED", reportFromDate, reportThruDate);

        if (UtilValidate.isNotEmpty(orderCompletedList)) {
            orderList.addAll(orderCompletedList);
        }

        if (UtilValidate.isNotEmpty(orderApprovedList)) {
            orderList.addAll(orderApprovedList);
        }

        //orderList = MpReportUtil.getOrderDateFilterList(delegator, "ORDER_COMPLETED", reportFromDate, reportThruDate);
        int counter_invoice_ids = 0;
        for (GenericValue orderHeader : orderList) {

            String orderId = (String) orderHeader.get("orderId");
            Timestamp entryDate = (Timestamp) orderHeader.get("entryDate");
            String orderStatus = (String) orderHeader.get("statusId");
            BigDecimal orderGrandTotal = (BigDecimal) orderHeader.get("grandTotal");
            String orderGrandTotalStr = MpReportUtil.replaceDecimalCharatcer(orderGrandTotal, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

            List<GenericValue> orderItemList = null;

            try {

            	EntityCondition itemCondition = EntityCondition.makeCondition(EntityOperator.OR,
            			EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_COMPLETED"),
                        EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_APPROVED"));
            	
                EntityCondition statusItemCondition = EntityCondition.makeCondition(EntityOperator.AND,
                        EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId),itemCondition);
                       

                orderItemList = delegator.findList("OrderItem", statusItemCondition, null, null, null, false);
            } catch (GenericEntityException gee) {
                Debug.logError(gee, module);
            }

            //get the order adjustments
            List<GenericValue> orderAdjustmentList = null;

            try {
                orderAdjustmentList = delegator.findList("OrderAdjustment", EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId), null, null, null, false);
            } catch (GenericEntityException gee) {
                Debug.logError(gee, module);
            }

            //Build the OrderReadHelper
            OrderReadHelper orh = new OrderReadHelper(orderHeader, orderAdjustmentList, orderItemList);
            
            //loop all the order items
            for (GenericValue orderItem : orderItemList) {

                StringBuilder reportRecord = new StringBuilder();

                String orderItemStatus = (String) orderItem.get("statusId");

                List<GenericValue> itemIssuances = orh.getOrderItemIssuances(orderItem);
                LinkedHashMap<String, String> issuanceFacilityMap = (LinkedHashMap) MpReportUtil.getItemIssuanceFacilityMap(itemIssuances, delegator);

                BigDecimal qtaSped = BigDecimal.ZERO;

                //list the issuances and add up all the shipped quantities for this item
                for (GenericValue itemIssuance : itemIssuances) {
                    BigDecimal issuedQty = (BigDecimal) itemIssuance.get("quantity");
                    qtaSped = qtaSped.add(issuedQty);
                }

                String qtaSpedStr = MpReportUtil.replaceDecimalCharatcer(qtaSped, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                String productId = (String) orderItem.get("productId");

                String orderItemSeqId = (String) orderItem.get("orderItemSeqId");

                BigDecimal qta = (BigDecimal) orderItem.get("quantity");

                BigDecimal unitListPrice = (BigDecimal) orderItem.get("unitListPrice"); //full price

                BigDecimal unitPrice = (BigDecimal) orderItem.get("unitPrice"); //eventually discounted price

                String unitListPriceStr = MpReportUtil.replaceDecimalCharatcer(unitListPrice, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);
                String unitPriceStr = MpReportUtil.replaceDecimalCharatcer(unitPrice, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);
                String qtaStr = MpReportUtil.replaceDecimalCharatcer(qta, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                String stag = MpReportUtil.getProductSeasonFromEcomSku(productId);

                String linea = MpReportUtil.getProductLineFromEcomSku(productId);

                String mpart = MpReportUtil.getMpCodArtFromEcomSku(productId);

                String color = MpReportUtil.getMpColorFromEcomSku(productId);

                String sz = MpReportUtil.getMpSizeFromEcomSku(productId);

                GenericValue childProduct = null;

                try {
                    childProduct = delegator.findOne("Product", UtilMisc.toMap("productId", productId), false);
                } catch (GenericEntityException gee) {
                    Debug.logError(gee, module);
                }

                ProductContentWrapper pcw = new ProductContentWrapper(null, childProduct, locale, "text/html");

                String descr = pcw.get("PRODUCT_NAME", "html").toString();

                String primCategId = MpReportUtil.getProductPrimaryCategoryId(delegator, productId);

                String categoryName = null;

                if (primCategId != null) {

                    GenericValue primaryCategory = null;

                    try {
                        primaryCategory = delegator.findOne("ProductCategory", UtilMisc.toMap("productCategoryId", primCategId), false);
                    } catch (GenericEntityException gee) {
                        Debug.logError(gee, module);
                    }

                    //System.out.println("--- primaryCategoryId ---"+primCategId);
                    if (primaryCategory != null) {

                        CategoryContentWrapper ccw = new CategoryContentWrapper(dispatcher, primaryCategory, locale, "text/html");

                        categoryName = (ccw.get("CATEGORY_NAME", "html") != null) ? ccw.get("CATEGORY_NAME", "html").toString() : "-";

                    } else {
                        categoryName = "-";
                    }

                } else {

                    categoryName = "-";

                }

                reportRecord.append(stag).append(";").append(linea).append(";");
                reportRecord.append(mpart).append(";").append(color).append(";");
                reportRecord.append(sz).append(";").append(descr).append(";");
                reportRecord.append(categoryName).append(";").append(orderId).append(";");

                //build order date from timestamp
                String order_date = MpReportUtil.getStringDateFromTimestamp(entryDate);

                reportRecord.append(order_date).append(";");
                
             // Località - Provincia
                List<GenericValue> shippingLocationList = orh.getShippingLocations();

                if (shippingLocationList.size() == 1) {

                    GenericValue shipPostalAddress = EntityUtil.getFirst(shippingLocationList);

                    String ship_country_geoId = (String) shipPostalAddress.get("countryGeoId");

                    reportRecord.append(ship_country_geoId).append(";");

                } else if (shippingLocationList.size() > 1) {

                    StringBuilder ship_province_builder = new StringBuilder();

                    for (GenericValue shippingLocation : shippingLocationList) {

                        String country_geoId = (String) shippingLocation.get("countryGeoId");

                        ship_province_builder.append(country_geoId).append(" ");

                    }

                    reportRecord.append(ship_province_builder).append(";");

                } else {

                    String ship_country_geo_id = "-";

                    reportRecord.append(ship_country_geo_id).append(";");
                }

                //order item reservations facilities: this string will be significant only if the item status is APPROVED
                List<GenericValue> orderItemShipGroups = orh.getOrderItemShipGroupAssocs(orderItem);

                String orderItemResFacilities = MpReportUtil.getOrderItemFacilityReservation(orderItemShipGroups, delegator);

                reportRecord.append(orderItemResFacilities).append(";");

                //add shipment facility and shipment date: this string will be significant only if item status if COMPLETED
                String shipFacilitiesAndDates = MpReportUtil.getFormatShipmentFacilitiesAndDates(issuanceFacilityMap, delegator);

                reportRecord.append(shipFacilitiesAndDates).append(";");

                //retrieve invoice data
                EntityCondition itemBillingCondition = EntityCondition.makeCondition(EntityOperator.AND,
                        EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId),
                        EntityCondition.makeCondition("orderItemSeqId", EntityOperator.EQUALS, orderItemSeqId));

                List<GenericValue> itemBillingList = null;

                try {
                    itemBillingList = delegator.findList("OrderItemBilling", itemBillingCondition, null, null, null, false);
                    
                } catch (GenericEntityException gee) {
                    Debug.logError(gee, module);
                }

                String ym_fatt = null;

                if (UtilValidate.isNotEmpty(itemBillingList)) {

                    GenericValue itemBillingLine = EntityUtil.getFirst(itemBillingList);

                    String invoiceId = (String) itemBillingLine.get("invoiceId");
                    
                    reportRecord.append(invoiceId).append(";");

                    GenericValue invoice = null;

                    try {
                        invoice = delegator.findOne("Invoice", UtilMisc.toMap("invoiceId", invoiceId), false);
                    } catch (GenericEntityException gee) {
                        Debug.logError(gee, module);
                    }

                    if (invoice != null) {

                        Timestamp invoiceDate = (Timestamp) invoice.get("invoiceDate");

                        ym_fatt = MpReportUtil.getStringYMFromTimestamp(invoiceDate);

                    } else {
                        ym_fatt = "-";
                    }

                } else {

                    ym_fatt = "-";

                }

                reportRecord.append(ym_fatt).append(";");

                String sku_descr_art = productId + " - " + descr;

                reportRecord.append(sku_descr_art).append(";");

                //status order item
                if ("ITEM_COMPLETED".equals(orderItemStatus)) {
                    reportRecord.append("SPEDITO").append(";");
                } else if ("ITEM_APPROVED".equals(orderItemStatus)) {
                    reportRecord.append("APPROVATO").append(";");
                } else {
                    reportRecord.append(orderItemStatus).append(";");
                }
                
                reportRecord.append(unitListPriceStr).append(";");
                reportRecord.append(qtaStr).append(";"); //ordered qty
                reportRecord.append(qtaSpedStr).append(";"); //shipped qty

                BigDecimal discount = MpReportUtil.calcDiscount(unitPrice, unitListPrice);

                String discountStr = MpReportUtil.replaceDecimalCharatcer(discount, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                reportRecord.append(discountStr).append(";");

                reportRecord.append(unitListPriceStr).append(";"); //full price
                
                reportRecord.append(unitPriceStr).append(";"); //discounted price
                
                //get order promocode applied
                Set<String> promocodeEntered = orh.getProductPromoCodesEntered();

                StringBuilder orderPromoCodes = new StringBuilder();

                if (!promocodeEntered.isEmpty()) {

                    Iterator<String> codeIter = promocodeEntered.iterator();

                    while (codeIter.hasNext()) {

                        orderPromoCodes.append(codeIter.next()).append(",");

                    }

                } else {
                    orderPromoCodes.append("-");
                }

                reportRecord.append(orderPromoCodes.toString()).append(";");
                
                BigDecimal orderRowSubTotal = unitPrice.multiply(qta);
                String orderRowSubTotalStr = MpReportUtil.replaceDecimalCharatcer(orderRowSubTotal, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                reportRecord.append(orderRowSubTotalStr).append(";"); //tot pagato arrotondato
                
                reportRecord.append(orderRowSubTotalStr).append(";"); //tot pagato

                reportRecord.append(orderGrandTotalStr).append(";"); //tot ordine
                
                //@Luca invoice id
                List<GenericValue> orderInvoices = null;
                String invoice_id = null;
                Timestamp invoice_creation_date = null;
                try {

                    orderInvoices = delegator.findList("OrderItemBilling", EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId), null, null, null, false);
                } catch (GenericEntityException gee) {
                    Debug.logError(gee, module);
                }    

                if (orderInvoices.size()> 1) {

                    invoice_id = (String) orderInvoices.get(counter_invoice_ids).get("invoiceId");
                    invoice_creation_date = (Timestamp) orderInvoices.get(counter_invoice_ids).get("createdStamp");
                    counter_invoice_ids ++;
                } else {

                    invoice_id = (String) orderInvoices.get(0).get("invoiceId");
                    invoice_creation_date = (Timestamp) orderInvoices.get(0).get("createdStamp");
                }

                reportRecord.append(invoice_id).append(";");
                reportRecord.append(invoice_creation_date).append(";");
                
                // Destinatario
                GenericValue shippingAddress = orh.getShippingAddress(DEFAULT_SHIP_GROUP_ID);
                String ISO3_COUNTRY = (String) shippingAddress.get("countryGeoId");

                reportRecord.append(ISO3_COUNTRY).append(";");

                
                //retrieve ReturnItems and ReturnHeader if any
                List<GenericValue> returnItemList = null;
                
                returnItemList = MpReportUtil.getOrderItmReturnItemList(delegator, orderId, orderItemSeqId);
                
                if (returnItemList != null && returnItemList.size() > 0) {

                    GenericValue returnItem = EntityUtil.getFirst(returnItemList);

                    GenericValue returnHeader = null;

                    try {
                        returnHeader = returnItem.getRelatedOne("ReturnHeader", false);
                    } catch (GenericEntityException gee) {
                        Debug.logError(gee, module);
                    }
                    
                    String returnReasonId = (String) returnItem.get("returnReasonId");                    
                    String reasonDescription = MpReportUtil.getReturnItemReasonDescription(delegator, returnReasonId);

                    //append reason
                    reportRecord.append(reasonDescription).append(";");

                    //append return id
                    reportRecord.append((String) returnItem.get("returnId")).append(";");

                    String ym_return = null;

                    //ym return
                    ym_return = MpReportUtil.getStringYMFromTimestamp((Timestamp) returnItem.get("lastUpdatedStamp"));

                    reportRecord.append(ym_return).append(";");

                    String returnCompletedDate = null;

                    //if return is completed set date
                    if (returnHeader.get("statusId") == "RETURN_COMPLETED") {

                        returnCompletedDate = MpReportUtil.getReturnChangeStatusDateString(delegator, (String) returnHeader.get("returnId"), "RETURN_COMPLETED");

                    } else {

                        returnCompletedDate = "RESO APERTO";

                    }

                    reportRecord.append(returnCompletedDate).append(";");
                    
                    //get list of specific itemm for this return id
                    List<GenericValue> returnItemReturnList = null;
                    
                    String returnId = (String) returnItem.get("returnId");
                
                    EntityCondition returnItemCondition = EntityCondition.makeCondition(EntityOperator.AND,
                                EntityCondition.makeCondition("returnId", EntityOperator.EQUALS, returnId),
                                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED"));
                    try {

                            returnItemReturnList = delegator.findList("ReturnItem", returnItemCondition, null, null, null, false);
                        } catch (GenericEntityException gee) {
                            Debug.logWarning(gee, module);
                        }
                
                
                    // Pagamento
                    List<GenericValue> paymentPrefs = orh.getPaymentPreferences();

                    String payment_gateway = MpReportUtil.getOrderPaymentMethod(paymentPrefs);

                    // Pcs resi
                    BigDecimal return_pcs = BigDecimal.ZERO;

                    return_pcs = MpReportUtil.getReturnTotalQuantity(returnItemReturnList);

                    String return_pcs_str = MpReportUtil.replaceDecimalCharatcer(return_pcs, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);
                    
                    // numero pezzi resi
                    reportRecord.append(return_pcs_str).append(";");

                    // Gateway Rimborso
                    reportRecord.append(payment_gateway).append(";");

                    // Note
                    reportRecord.append("-").append(";");
                    
                }

                //write the file
                try {
                    if (writer != null) {
                        writer.append(reportRecord.toString());
                        writer.append(System.lineSeparator());
                    }
                } catch (IOException ex) {
                    Debug.logError(ex, module);
                }

                reportRecord = null;

            }

        }

        //Write to the file
        try {
            writer.close();
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }
        boolean emailSent = false;
        /*

        
        //Sending report by email
        String emailObject = tenantId + " - Sales Report"; 
        
        if(UtilValidate.isEmpty(mailCcAddress)) {
        
            emailSent = MpEmailServices.sendMailWithAttachment(outFilePath, outfilename, mailFromAddress, mailToAddress, null, null, emailObject, "text/plain", username, password, dispatcher);

        }else{
            
            //sendCC
            
            emailSent = MpEmailServices.sendMailWithAttachment(outFilePath, outfilename, mailFromAddress, mailToAddress, mailCcAddress, null, emailObject, "text/plain", username, password, dispatcher);
        }
        */
        return ServiceUtil.returnSuccess("Sales Report successfully created. Mail Sent? " + emailSent);

    }

    /**
     *
     * @param dctx
     * @param context
     * @return
     */
    public static Map<String, Object> createOrderReport(DispatchContext dctx, Map<String, Object> context) {

        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();

        int yyyy = -1, mm = -1, dd = -1, h = -1, m = -1, s = -1;

        String outFilePath = null;
        File outputFile = null;
        FileWriter writer = null;

        //String outDirPath = (String) context.get("outDirPath");
        String freeShippingPromos = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "freeshipping.promos", null, delegator);
        String outDirPath = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "order.report.outdir", null, delegator);
        String username = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.username", null, delegator);
        String password = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.password", null, delegator);
        String mailFromAddress = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.mail.fromAddress", null, delegator);

        Timestamp reportFromDate = (Timestamp) context.get("reportFromDate");
        Timestamp reportThruDate = (Timestamp) context.get("reportThruDate");
        String mailToAddress = (String) context.get("mailToAddress");
        String mailCcAddress = (String) context.get("mailCcAddress");
        Locale locale = (Locale) context.get("locale");

        String tenantId = delegator.getDelegatorTenantId();

        //set up calendar
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        yyyy = calendar.get(Calendar.YEAR);
        mm = calendar.get(Calendar.MONTH) + 1;
        dd = calendar.get(Calendar.DAY_OF_MONTH);
        h = calendar.get(Calendar.HOUR_OF_DAY);
        m = calendar.get(Calendar.MINUTE);
        s = calendar.get(Calendar.SECOND);

        String outfilename = ORDER_REPORT_FILENAME + "_" + tenantId + "_" + yyyy
                + mm + dd + "_" + h + m + s + REPORT_FILE_EXT;

        if (outDirPath.endsWith("/")) {

            outFilePath = outDirPath + outfilename;

        } else {

            outFilePath = outDirPath + "/" + outfilename;

        }

        //Create the file
        outputFile = new File(outFilePath);

        try {
            writer = new FileWriter(outputFile);
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }

        if (!outputFile.exists()) {
            Debug.logError("Output file " + outFilePath + "not created. Abort.", module);
            return ServiceUtil.returnFailure("Output file " + outFilePath + "not created. Abort.");
        }

        //write the csv file header
        try {
            writer.append(ORDER_REPORT_CSV_HEADER);
            writer.append(System.lineSeparator());
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }

        List<GenericValue> orderList = new ArrayList<>();
        List<GenericValue> orderCompletedList = null;
        List<GenericValue> orderApprovedList = null;

        orderCompletedList = MpReportUtil.getOrderDateFilterList(delegator, "ORDER_COMPLETED", reportFromDate, reportThruDate);
        orderApprovedList = MpReportUtil.getOrderDateFilterList(delegator, "ORDER_APPROVED", reportFromDate, reportThruDate);

        if (UtilValidate.isNotEmpty(orderCompletedList)) {
            orderList.addAll(orderCompletedList);
        }

        if (UtilValidate.isNotEmpty(orderApprovedList)) {
            orderList.addAll(orderApprovedList);
        }

        for (GenericValue orderHeader : orderList) {

            StringBuilder reportRecord = new StringBuilder();

            List<StringBuilder> multipleReturnBuilderList = null;

            String orderId = (String) orderHeader.get("orderId");

            String order_status = (String) orderHeader.get("statusId");

            Timestamp entryDate = (Timestamp) orderHeader.get("entryDate");

            List<GenericValue> orderItemList = null;

            try {

            	EntityCondition itemCondition = EntityCondition.makeCondition(EntityOperator.OR,
            			EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_COMPLETED"),
                        EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "ITEM_APPROVED"));
            	
                EntityCondition completedItemCondition = EntityCondition.makeCondition(EntityOperator.AND,
                        EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId),itemCondition);
                        

                orderItemList = delegator.findList("OrderItem", completedItemCondition, null, null, null, false);
            } catch (GenericEntityException gee) {
                Debug.logError(gee, module);
            }

            //get the order adjustments
            List<GenericValue> orderAdjustmentList = null;

            try {
                orderAdjustmentList = delegator.findList("OrderAdjustment", EntityCondition.makeCondition("orderId", EntityOperator.EQUALS, orderId), null, null, null, false);
            } catch (GenericEntityException gee) {
                Debug.logError(gee, module);
            }

            //Build the OrderReadHelper
            OrderReadHelper orh = new OrderReadHelper(orderHeader, orderAdjustmentList, orderItemList);

            // Y/M Ord
            String ym_ord = null;

            ym_ord = MpReportUtil.getStringYMFromTimestamp(entryDate);

            reportRecord.append(ym_ord).append(";");

            //Data
            String orderDate = MpReportUtil.getStringDateFromTimestamp(entryDate);

            reportRecord.append(orderDate).append(";");
            
            // Data Ordine
            String orderDateTime = MpReportUtil.getStringDateTimeFromTimestamp(entryDate);

            reportRecord.append(orderDateTime).append(";");

            List<GenericValue> orderStatuses = orh.getOrderStatuses();

            Timestamp orderCompletedTs = MpReportUtil.getOrderStatusChangeDate(orderStatuses, "ORDER_COMPLETED");

            String orderShipTime = null;

            if (orderCompletedTs != null) {

                orderShipTime = MpReportUtil.getStringDateFromTimestamp(orderCompletedTs);

            } else {

                orderShipTime = "-";

            }

            // Data Spedizione: empty field because Elisa get this into Sales Report.
            reportRecord.append("-").append(";");

            Timestamp orderReceivedTs = MpReportUtil.getOrderStatusChangeDate(orderStatuses, "ORDER_APPROVED");

            int shipDiffDays = MpReportUtil.orderReceivedShippedDaysDiff(orderReceivedTs, orderCompletedTs);

            // Sped + X gg.
            reportRecord.append(shipDiffDays).append(";");

            // Data Vendita
            reportRecord.append(orderShipTime).append(";");

            int yyyy_vendita = MpReportUtil.getYearFromTimestamp(orderCompletedTs);

            int mm_vendita = MpReportUtil.getMonthFromTimestamp(orderCompletedTs);

            // Anno V
            reportRecord.append(yyyy_vendita).append(";");

            // Mese V
            reportRecord.append(mm_vendita).append(";");

            // Data Ordine CMS
            reportRecord.append(entryDate).append(";");

            // Ordine #
            reportRecord.append(orderId).append(";");
            
            //Debug.logWarning("orderId:" +orderId, module);

            // Stato Ordine
            reportRecord.append(order_status).append(";");

            // Fattura a
            GenericValue billToParty = orh.getBillToParty();

            Timestamp billPartyCreationDate = billToParty.getTimestamp("createdStamp");

            String billPartyCreationDateString = MpReportUtil.getStringDateFromTimestamp(billPartyCreationDate);

            String billToPartyId = (String) billToParty.get("partyId");

            String partyName = MpReportUtil.getPartyFirstLastName(delegator, billToPartyId);

            partyName = partyName + "(" + billToPartyId + ")";

            reportRecord.append(partyName).append(";");

            // Negozio
            GenericValue productStore = orh.getProductStore();

            String storeName = (productStore != null) ? ((String) productStore.get("storeName")) : "-";

            reportRecord.append(storeName).append(";");

            // Tot ordine
            BigDecimal tot_ordine = orh.getOrderGrandTotal();

            String tot_ordine_str = MpReportUtil.replaceDecimalCharatcer(tot_ordine, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

            reportRecord.append(tot_ordine_str).append(";");

            // Pagamento
            List<GenericValue> paymentPrefs = orh.getPaymentPreferences();

            String payment_gateway = MpReportUtil.getOrderPaymentMethod(paymentPrefs);

            reportRecord.append(payment_gateway).append(";");

            /*TODO RETRIEVE ANOTHER WAY*/
            // Pcs
            BigDecimal order_pcs = orh.getTotalOrderItemsQuantity();

            String order_pcs_str = MpReportUtil.replaceDecimalCharatcer(order_pcs, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

            reportRecord.append(order_pcs_str).append(";");

            // Spese Trasporto
            BigDecimal shipping_charges = BigDecimal.ZERO;

            BigDecimal promotion_adjustment_amount = BigDecimal.ZERO;

            BigDecimal totalShippingDelivery = BigDecimal.ZERO;

            //String shipping_charges_str = null;
            String total_shipping_delivery_str = null;
            
            Boolean isPromotionAdjustment = MpReportUtil.isPromotionAdjustment(orderAdjustmentList, "PROMOTION_ADJUSTMENT", freeShippingPromos);

            //Debug.logWarning("isPromotionAdjustment:" +isPromotionAdjustment, module);
            
            if (isPromotionAdjustment) {
            	
                promotion_adjustment_amount = MpReportUtil.getOrderShippingCharges(orderAdjustmentList, "PROMOTION_ADJUSTMENT", freeShippingPromos);

                shipping_charges = MpReportUtil.getOrderShippingCharges(orderAdjustmentList, "SHIPPING_CHARGES", freeShippingPromos);

                totalShippingDelivery = totalShippingDelivery.add(promotion_adjustment_amount.add(shipping_charges));

                total_shipping_delivery_str = MpReportUtil.replaceDecimalCharatcer(totalShippingDelivery, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                reportRecord.append(total_shipping_delivery_str).append(";");
                
                //Debug.logWarning("total_shipping_delivery_str if isPromotionAdjustment:" +total_shipping_delivery_str, module);

            } else {

                shipping_charges = MpReportUtil.getOrderShippingCharges(orderAdjustmentList, "SHIPPING_CHARGES", freeShippingPromos);

                total_shipping_delivery_str = MpReportUtil.replaceDecimalCharatcer(shipping_charges, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                reportRecord.append(total_shipping_delivery_str).append(";");
                
                //Debug.logWarning("total_shipping_delivery_str not isPromotionAdjustment:" +total_shipping_delivery_str, module);

            }
            
            //Debug.logWarning("reportRecord" +reportRecord.toString(), module);

            // Tot Venduto merce    
            BigDecimal orderItemTotalAmount = orh.getOrderItemsSubTotal(orderItemList, orderAdjustmentList);

            String orderItemTotalAmountStr = MpReportUtil.replaceDecimalCharatcer(orderItemTotalAmount, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

            reportRecord.append(orderItemTotalAmountStr).append(";");

            // Sconti -get order promocode applied
            Set<String> promocodeEntered = orh.getProductPromoCodesEntered();

            StringBuilder orderPromoCodes = new StringBuilder();

            if (!promocodeEntered.isEmpty()) {
                Iterator<String> codeIter = promocodeEntered.iterator();

                while (codeIter.hasNext()) {
                    orderPromoCodes.append(codeIter.next()).append(",");
                }

            } else {
                orderPromoCodes.append("-");
            }

            reportRecord.append(orderPromoCodes.toString()).append(";");

            // Destinatario
            GenericValue shipToParty = orh.getShipToParty();

            String shipToPartyId = (String) shipToParty.get("partyId");

            String shipToPartyName = MpReportUtil.getPartyFirstLastName(delegator, shipToPartyId);

            shipToPartyName = shipToPartyName + "(" + shipToPartyId + ")";

            reportRecord.append(shipToPartyName).append(";");

            // Email
            String order_email = orh.getOrderEmailString();

            reportRecord.append(order_email).append(";");

            // Località - Provincia
            List<GenericValue> shippingLocationList = orh.getShippingLocations();

            if (shippingLocationList.size() == 1) {

                GenericValue shipPostalAddress = EntityUtil.getFirst(shippingLocationList);

                String ship_city = (String) shipPostalAddress.get("city");
                String ship_province = (String) shipPostalAddress.get("stateProvinceGeoId");

                reportRecord.append(ship_city).append(";");
                reportRecord.append(ship_province).append(";");

            } else if (shippingLocationList.size() > 1) {

                StringBuilder ship_city_builder = new StringBuilder();
                StringBuilder ship_province_builder = new StringBuilder();

                for (GenericValue shippingLocation : shippingLocationList) {

                    String city = (String) shippingLocation.get("city");
                    String province = (String) shippingLocation.get("stateProvinceGeoId");

                    ship_city_builder.append(city).append(" ");
                    ship_province_builder.append(province).append(" ");

                }

                reportRecord.append(ship_city_builder.toString()).append(";");
                reportRecord.append(ship_province_builder).append(";");

            } else {

                String ship_city = "-";
                String ship_province = "-";

                reportRecord.append(ship_city).append(";");
                reportRecord.append(ship_province).append(";");
            }

            //note
            reportRecord.append("-").append(";");

            /**
             * ** START RETURN PART ***
             */
            //Get orderReturnItems
            List<GenericValue> orderReturnItems = orh.getOrderReturnItems();

            boolean hasOrderReturns = MpReportUtil.hasOrderReturn(orderReturnItems);

            //if there are returns
            if (hasOrderReturns) {

                boolean hasOrderMultipleReturns = MpReportUtil.hasOrderMultipleReturns(orderReturnItems);

                if (hasOrderMultipleReturns) {

                    //System.out.println("Order "+orderId+" has associated multiple returns");
                    multipleReturnBuilderList = new ArrayList<>();

                    /*
                     * for the first return data nothing change compared to order-single-return-case case 
                     * for the second, third,... lines ( == different returnId ) should be added  a new row, that is almost-empty
                     * for the order side and filled with data for the return part.
                     */
                    List<String> orderReturnIdList = MpReportUtil.getOrderReturnIdList(orderReturnItems);

                    /* ###### FIRST LINE ###### */
                    //string first return id
                    String returnId = orderReturnIdList.remove(0);

                    // Reso#
                    reportRecord.append(returnId).append(";");

                    GenericValue returnHeader = null;

                    try {

                        returnHeader = delegator.findOne("ReturnHeader", UtilMisc.toMap("returnId", returnId), false);

                    } catch (GenericEntityException gee) {
                        Debug.logError(gee, module);
                    }

                    // Data Richiesta Reso
                    Timestamp returnEntryDateTs = (Timestamp) returnHeader.get("entryDate");

                    String return_entry_date = MpReportUtil.getStringDateTimeFromTimestamp(returnEntryDateTs);

                    reportRecord.append(return_entry_date).append(";");

                    // Reso da
                    String returnPartyIdFrom = (String) returnHeader.get("fromPartyId");

                    String returnFromPartyName = MpReportUtil.getPartyFirstLastName(delegator, returnPartyIdFrom);

                    returnFromPartyName = returnFromPartyName + "(" + returnPartyIdFrom + ")";

                    reportRecord.append(returnFromPartyName).append(";");

                    // Stato
                    String returnStatus = null;
                    boolean returnCompleted = false;

                    if (((String) returnHeader.get("statusId")).equals("RETURN_COMPLETED")) {

                        returnStatus = "RESO COMPLETATO";
                        returnCompleted = true;

                    } else if (!((String) returnHeader.get("statusId")).equals("RETURN_CANCELLED")) {

                        returnStatus = "RESO APERTO";

                    }

                    reportRecord.append(returnStatus).append(";");

                    //get list of specific itemm for this return id
                    List<GenericValue> returnItemReturnList = null;

                    EntityCondition returnItemCondition = EntityCondition.makeCondition(EntityOperator.AND,
                            EntityCondition.makeCondition("returnId", EntityOperator.EQUALS, returnId),
                            EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED"));

                    try {
                        returnItemReturnList = delegator.findList("ReturnItem", returnItemCondition, null, null, null, false);
                    } catch (GenericEntityException gee) {
                        Debug.logWarning(gee, module);
                    }

                    // Tipo rimborso
                    String refundType = MpReportUtil.getReturnItemsRefundTypeList(returnItemReturnList);

                    reportRecord.append(refundType).append(";");

                    // Metodo
                    String returnMethod = MpReportUtil.getReturnItemsReturnMethodTypeList(returnItemReturnList);

                    reportRecord.append(returnMethod).append(";");

                    // Data ricezione
                    String returnEntryDateString = MpReportUtil.getStringDateFromTimestamp(returnEntryDateTs);

                    reportRecord.append(returnEntryDateString).append(";");

                    if (returnCompleted) {

                        // Tot. Rimborsato
                        BigDecimal refundTotal = MpReportUtil.getRetunItemReturnableTotal(returnItemReturnList);

                        String refundTotalStr = MpReportUtil.replaceDecimalCharatcer(refundTotal, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);
                            
                        reportRecord.append(refundTotalStr).append(";");

                        // Scadenza promocode
                        String returnPromocode = "-";

                        reportRecord.append(returnPromocode).append(";");

                        // Mese check F.
                        reportRecord.append("-").append(";");

                        
                        List<GenericValue> returnStatusList = MpReportUtil.getReturnStatusList(delegator, returnId);

                        // Y/M reso
                        Timestamp returnCompletedDateTs = MpReportUtil.getReturnStatusChangeDate(returnStatusList, "RETURN_COMPLETED");

                        String ym_return_completed = MpReportUtil.getStringYMFromTimestamp(returnCompletedDateTs);

                        reportRecord.append(ym_return_completed).append(";");

                        // Data chiusura
                        String returnCompletedDate = MpReportUtil.getReturnChangeStatusDateString(delegator, (String) returnHeader.get("returnId"), "RETURN_COMPLETED");

                        reportRecord.append(returnCompletedDate).append(";");

                        // Pcs resi
                        BigDecimal return_pcs = BigDecimal.ZERO;

                        return_pcs = MpReportUtil.getReturnTotalQuantity(returnItemReturnList);

                        String return_pcs_str = MpReportUtil.replaceDecimalCharatcer(return_pcs, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                        reportRecord.append(return_pcs_str).append(";");

                        // Gateway Rimborso
                        reportRecord.append(payment_gateway).append(";");

                        // Rif Ordine #
                        reportRecord.append(orderId).append(";");

                        // Note
                        reportRecord.append("-").append(";");

                    } else {

                        // Tot. Rimborsato
                        reportRecord.append("-").append(";");

                        // Scadenza Promocode
                        reportRecord.append("-").append(";");

                        // Mese check F.
                        reportRecord.append("-").append(";");

                        // Y/M Reso
                        reportRecord.append("-").append(";");

                        // Data chiusura
                        reportRecord.append("-").append(";");

                        // Pcs resi
                        BigDecimal return_pcs = BigDecimal.ZERO;

                        return_pcs = MpReportUtil.getReturnTotalQuantity(returnItemReturnList);

                        String return_pcs_str = MpReportUtil.replaceDecimalCharatcer(return_pcs, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                        reportRecord.append(return_pcs_str).append(";");

                        // Gateway rimborso
                        reportRecord.append(payment_gateway).append(";");

                        // Rif.Ordine#
                        reportRecord.append(orderId).append(";");

                        //note
                        reportRecord.append("-").append(";");

                    }

                    /* ###### END FIRST LINE ###### */
                    /* ###### OTHER LINES ###### */
                    for (String _returnId : orderReturnIdList) {

                        StringBuilder newReturnRow = new StringBuilder();

                        // Order part
                        for (int i = 0; i < 23; i++) {
                            newReturnRow.append("-").append(";");
                        }

                        // Reso#
                        newReturnRow.append(_returnId).append(";");

                        GenericValue _returnHeader = null;

                        try {

                            _returnHeader = delegator.findOne("ReturnHeader", UtilMisc.toMap("returnId", _returnId), false);

                        } catch (GenericEntityException gee) {
                            Debug.logError(gee, module);
                        }

                        // Data richiesta reso
                        Timestamp _returnEntryDateTs = (Timestamp) _returnHeader.get("entryDate");

                        String _return_entry_date = MpReportUtil.getStringDateTimeFromTimestamp(_returnEntryDateTs);

                        newReturnRow.append(_return_entry_date).append(";");

                        // Reso da
                        String _returnPartyIdFrom = (String) _returnHeader.get("fromPartyId");

                        String _returnFromPartyName = MpReportUtil.getPartyFirstLastName(delegator, _returnPartyIdFrom);

                        _returnFromPartyName = _returnFromPartyName + "(" + _returnPartyIdFrom + ")";

                        newReturnRow.append(_returnFromPartyName).append(";");

                        // Stato
                        String _returnStatus = null;
                        boolean _returnCompleted = false;

                        if (((String) _returnHeader.get("statusId")).equals("RETURN_COMPLETED")) {

                            _returnStatus = "RESO COMPLETATO";
                            _returnCompleted = true;

                        } else if (!((String) _returnHeader.get("statusId")).equals("RETURN_CANCELLED")) {

                            _returnStatus = "RESO APERTO";

                        }

                        newReturnRow.append(_returnStatus).append(";");

                        //get list of specific itemm for this return id
                        List<GenericValue> _returnItemReturnList = null;

                        EntityCondition _returnItemCondition = EntityCondition.makeCondition(EntityOperator.AND,
                                EntityCondition.makeCondition("returnId", EntityOperator.EQUALS, _returnId),
                                EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED"));

                        try {
                            _returnItemReturnList = delegator.findList("ReturnItem", _returnItemCondition, null, null, null, false);
                        } catch (GenericEntityException gee) {
                            Debug.logWarning(gee, module);
                        }

                        // Tipo rimborso
                        String _refundType = MpReportUtil.getReturnItemsRefundTypeList(_returnItemReturnList);

                        newReturnRow.append(_refundType).append(";");

                        // Metodo
                        String _returnMethod = MpReportUtil.getReturnItemsReturnMethodTypeList(_returnItemReturnList);

                        newReturnRow.append(_returnMethod).append(";");

                        // Data ricezione
                        String _returnEntryDateString = MpReportUtil.getStringDateFromTimestamp(_returnEntryDateTs);

                        newReturnRow.append(_returnEntryDateString).append(";");

                        if (_returnCompleted) {

                            // Tot. Rimborsato    
                            BigDecimal refundTotal = MpReportUtil.getRetunItemReturnableTotal(_returnItemReturnList);

                            String refundTotalStr = MpReportUtil.replaceDecimalCharatcer(refundTotal, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                            newReturnRow.append(refundTotalStr).append(";");

                            // Scadenza Promocode
                            String returnPromocode = "-";

                            newReturnRow.append(returnPromocode).append(";");

                            // Mese check F.
                            newReturnRow.append(";");

                            
                            List<GenericValue> returnStatusList = MpReportUtil.getReturnStatusList(delegator, _returnId);

                            // Y/M reso
                            Timestamp returnCompletedDateTs = MpReportUtil.getReturnStatusChangeDate(returnStatusList, "RETURN_COMPLETED");

                            String ym_return_completed = MpReportUtil.getStringYMFromTimestamp(returnCompletedDateTs);

                            newReturnRow.append(ym_return_completed).append(";");

                            // Data chiusura
                            String returnCompletedDate = MpReportUtil.getReturnChangeStatusDateString(delegator, (String) _returnHeader.get("returnId"), "RETURN_COMPLETED");

                            newReturnRow.append(returnCompletedDate).append(";");

                            // Pcs resi
                            BigDecimal return_pcs = BigDecimal.ZERO;

                            return_pcs = MpReportUtil.getReturnTotalQuantity(_returnItemReturnList);

                            String return_pcs_str = MpReportUtil.replaceDecimalCharatcer(return_pcs, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                            newReturnRow.append(return_pcs_str).append(";");

                            // Gateway rimborso
                            newReturnRow.append(payment_gateway).append(";");

                            // Rif.Ordine#
                            newReturnRow.append(orderId).append(";");

                            // Note
                            newReturnRow.append("-").append(";");

                        } else {

                            // Tot. Rimborsato
                            newReturnRow.append("-").append(";");

                            // Scadenza promocode
                            newReturnRow.append("-").append(";");

                            // Mese check F.
                            newReturnRow.append("-").append(";");

                            // Y/M Reso
                            newReturnRow.append("-").append(";");

                            // Data chiusura
                            newReturnRow.append(";");

                            // Pcs resi
                            BigDecimal return_pcs = BigDecimal.ZERO;

                            return_pcs = MpReportUtil.getReturnTotalQuantity(_returnItemReturnList);

                            String return_pcs_str = MpReportUtil.replaceDecimalCharatcer(return_pcs, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                            newReturnRow.append(return_pcs_str).append(";");

                            // Gateway rimborso
                            newReturnRow.append(payment_gateway).append(";");

                            // Rif.Ordine#
                            newReturnRow.append(orderId).append(";");

                            // Note
                            newReturnRow.append("-").append(";");

                        }

                        multipleReturnBuilderList.add(newReturnRow);

                    } //end loop on other return ids

                    /* ###### END OTHER LINES ###### */
                } else {

                    //System.out.println("Order "+orderId+" has  one return");
                    List<String> orderReturnIdList = MpReportUtil.getOrderReturnIdList(orderReturnItems);

                    //should have just one element
                    String returnId = orderReturnIdList.get(0);

                    // Reso#
                    reportRecord.append(returnId).append(";");

                    GenericValue returnHeader = null;

                    try {

                        returnHeader = delegator.findOne("ReturnHeader", UtilMisc.toMap("returnId", returnId), false);

                    } catch (GenericEntityException gee) {
                        Debug.logError(gee, module);
                    }

                    // Data richiesta reso
                    Timestamp returnEntryDateTs = (Timestamp) returnHeader.get("entryDate");
                    
                    //Debug.logWarning("returnEntryDateTs:" +returnEntryDateTs, module);

                    String return_entry_date = MpReportUtil.getStringDateTimeFromTimestamp(returnEntryDateTs);

                    reportRecord.append(return_entry_date).append(";");

                    // Reso da
                    String returnPartyIdFrom = (String) returnHeader.get("fromPartyId");

                    String returnFromPartyName = MpReportUtil.getPartyFirstLastName(delegator, returnPartyIdFrom);

                    returnFromPartyName = returnFromPartyName + "(" + returnPartyIdFrom + ")";

                    reportRecord.append(returnFromPartyName).append(";");

                    // Stato
                    String returnStatus = null;
                    boolean returnCompleted = false;

                    if (((String) returnHeader.get("statusId")).equals("RETURN_COMPLETED")) {

                        returnStatus = "RESO COMPLETATO";
                        returnCompleted = true;

                    } else if (!((String) returnHeader.get("statusId")).equals("RETURN_CANCELLED")) {

                        returnStatus = "RESO APERTO";

                    }

                    reportRecord.append(returnStatus).append(";");

                    //get list of specific itemm for this return id
                    List<GenericValue> returnItemReturnList = null;

                    EntityCondition returnItemCondition = EntityCondition.makeCondition(EntityOperator.AND,
                            EntityCondition.makeCondition("returnId", EntityOperator.EQUALS, returnId),
                            EntityCondition.makeCondition("statusId", EntityOperator.NOT_EQUAL, "RETURN_CANCELLED"));

                    try {
                        returnItemReturnList = delegator.findList("ReturnItem", returnItemCondition, null, null, null, false);
                    } catch (GenericEntityException gee) {
                        Debug.logWarning(gee, module);
                    }

                    // Tipo rimborso
                    String refundType = MpReportUtil.getReturnItemsRefundTypeList(returnItemReturnList);

                    reportRecord.append(refundType).append(";");

                    // Metodo
                    String returnMethod = MpReportUtil.getReturnItemsReturnMethodTypeList(returnItemReturnList);

                    reportRecord.append(returnMethod).append(";");

                    // Data ricezione
                    String returnEntryDateString = MpReportUtil.getStringDateFromTimestamp(returnEntryDateTs);

                    reportRecord.append(returnEntryDateString).append(";");

                    if (returnCompleted) {

                        // Tot.Rimborsato
                        BigDecimal refundTotal = MpReportUtil.getRetunItemReturnableTotal(returnItemReturnList);

                        String refundTotalStr = MpReportUtil.replaceDecimalCharatcer(refundTotal, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                        reportRecord.append(refundTotalStr).append(";");

                        // Scadenza promocode
                        String returnPromocode = "-";

                        reportRecord.append(returnPromocode).append(";");

                        // Mese check F.
                        reportRecord.append(";");

                        //return status
                        List<GenericValue> returnStatusList = MpReportUtil.getReturnStatusList(delegator, returnId);

                        // Y/M reso
                        Timestamp returnCompletedDateTs = MpReportUtil.getReturnStatusChangeDate(returnStatusList, "RETURN_COMPLETED");

                        String ym_return_completed = MpReportUtil.getStringYMFromTimestamp(returnCompletedDateTs);

                        reportRecord.append(ym_return_completed).append(";");

                        // Data chiusura
                        String returnCompletedDate = MpReportUtil.getReturnChangeStatusDateString(delegator, (String) returnHeader.get("returnId"), "RETURN_COMPLETED");

                        reportRecord.append(returnCompletedDate).append(";");

                        // Pcs resi
                        BigDecimal return_pcs = BigDecimal.ZERO;

                        return_pcs = MpReportUtil.getReturnTotalQuantity(returnItemReturnList);

                        String return_pcs_str = MpReportUtil.replaceDecimalCharatcer(return_pcs, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                        reportRecord.append(return_pcs_str).append(";");

                        // Gateway rimborso
                        reportRecord.append(payment_gateway).append(";");

                        // Rif.Ordine#
                        reportRecord.append(orderId).append(";");

                        //note
                        reportRecord.append("-").append(";");

                    } else {

                        // Tot Rimborsato
                        reportRecord.append("-").append(";");

                        // Scadenza promocode
                        reportRecord.append("-").append(";");

                        // Mese check F.
                        reportRecord.append("-").append(";");

                        // Y/M Reso
                        reportRecord.append("-").append(";");

                        // Data chiusura
                        reportRecord.append("-").append(";");

                        // Pcs resi
                        BigDecimal return_pcs = BigDecimal.ZERO;

                        return_pcs = MpReportUtil.getReturnTotalQuantity(returnItemReturnList);

                        String return_pcs_str = MpReportUtil.replaceDecimalCharatcer(return_pcs, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

                        reportRecord.append(return_pcs_str).append(";");

                        // Gateway rimborso
                        reportRecord.append(payment_gateway).append(";");

                        // Rif.Ordine#
                        reportRecord.append(orderId).append(";");

                        // note
                        reportRecord.append("-").append(";");

                    }

                }

            } else {

                //System.out.println("Order "+orderId+"has no returns");
                //add 17 empty fields 
                for (int i = 0; i < 16; i++) {
                    reportRecord.append("-").append(";");
                }

            }

            /**
             * ** END RETURN PART ***
             */
            // Data registazione cliente.
            reportRecord.append(billPartyCreationDateString).append(";");

            //write the file
            try {
                writer.append(reportRecord.toString());
                writer.append(System.lineSeparator());

                if (multipleReturnBuilderList != null && multipleReturnBuilderList.size() > 0) {

                    for (StringBuilder multipleReturnBuilderItem : multipleReturnBuilderList) {

                        writer.append(multipleReturnBuilderItem.toString());
                        writer.append(System.lineSeparator());
                    }

                }

            } catch (IOException ex) {
                Debug.logError(ex, module);
            }

            reportRecord = null;

        } //end looping orders

        //Write to the file
        try {
            writer.close();
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }

        //Sending report by email
        boolean emailSent = false;
        String emailObject = tenantId + " - Order Report";

        if (UtilValidate.isEmpty(mailCcAddress)) {

            emailSent = MpEmailServices.sendMailWithAttachment(outFilePath, outfilename, mailFromAddress, mailToAddress, null, null, emailObject, "text/plain", username, password, dispatcher);

        } else {

            //sendCC
            emailSent = MpEmailServices.sendMailWithAttachment(outFilePath, outfilename, mailFromAddress, mailToAddress, mailCcAddress, null, emailObject, "text/plain", username, password, dispatcher);
        }

        return ServiceUtil.returnSuccess("Order Report successfully created. Mail sent? " + emailSent);

    }

    public Map<String, Object> createCustomerOrderReport(DispatchContext dctx, Map<String, Object> context) {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();

        int yyyy = -1, mm = -1, dd = -1, h = -1, m = -1, s = -1;

        String outFilePath = null;
        File outputFile = null;
        FileWriter writer = null;

        //String outDirPath = (String) context.get("outDirPath");
        String outDirPath = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "customer.report.outdir", null, delegator);
        String username = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.username", null, delegator);
        String password = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.password", null, delegator);
        String mailFromAddress = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.mail.fromAddress", null, delegator);

        Timestamp reportFromDate = (Timestamp) context.get("reportFromDate");
        Timestamp reportThruDate = (Timestamp) context.get("reportThruDate");
        String mailToAddress = (String) context.get("mailToAddress");
        String mailCcAddress = (String) context.get("mailCcAddress");

        Locale locale = (Locale) context.get("locale");

        String tenantId = delegator.getDelegatorTenantId();

        //set up calendar
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        yyyy = calendar.get(Calendar.YEAR);
        mm = calendar.get(Calendar.MONTH) + 1;
        dd = calendar.get(Calendar.DAY_OF_MONTH);
        h = calendar.get(Calendar.HOUR_OF_DAY);
        m = calendar.get(Calendar.MINUTE);
        s = calendar.get(Calendar.SECOND);

        String outfilename = CUSTOMER_ORDER_REPORT_FILENAME + "_" + tenantId + "_" + yyyy
                + mm + dd + "_" + h + m + s + REPORT_FILE_EXT;

        //System.out.println("Customer Order Report outDir:" + outDirPath);
        //System.out.println("reportFromDate:" + reportFromDate);
        //System.out.println("reportThruDate:" + reportThruDate);

        if (outDirPath.endsWith("/")) {

            outFilePath = outDirPath + outfilename;

        } else {

            outFilePath = outDirPath + "/" + outfilename;

        }

        //Create the file
        outputFile = new File(outFilePath);

        try {
            writer = new FileWriter(outputFile);
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }

        if (!outputFile.exists()) {
            Debug.logError("Output file " + outFilePath + "not created. Abort.", module);
            return ServiceUtil.returnFailure("Output file " + outFilePath + "not created. Abort.");
        }

        //write the csv file header
        try {
            writer.append(CUSTOMER_ORDER_REPORT_CSV_HEADER);
            writer.append(System.lineSeparator());
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }

        List<GenericValue> orderList = new ArrayList<>();
        List<GenericValue> orderCompletedList = null;
        List<GenericValue> orderApprovedList = null;

        orderCompletedList = MpReportUtil.getOrderDateFilterList(delegator, "ORDER_COMPLETED", reportFromDate, reportThruDate);
        orderApprovedList = MpReportUtil.getOrderDateFilterList(delegator, "ORDER_APPROVED", reportFromDate, reportThruDate);

        if (UtilValidate.isNotEmpty(orderCompletedList)) {
            orderList.addAll(orderCompletedList);
        }

        if (UtilValidate.isNotEmpty(orderApprovedList)) {
            orderList.addAll(orderApprovedList);
        }

        //System.out.println("orderList: " + orderList);

        //orderList = MpReportUtil.getOrderDateFilterList(delegator, "ORDER_COMPLETED", reportFromDate, reportThruDate);
        for (GenericValue orderHeader : orderList) {

            String orderId = (String) orderHeader.get("orderId");

            //System.out.println("orderId: " + orderId);

            BigDecimal orderGrandTotal = (BigDecimal) orderHeader.get("grandTotal");
            String orderGrandTotalStr = MpReportUtil.replaceDecimalCharatcer(orderGrandTotal, STD_DECIMAL_CHAR, EXC_DECIMAL_CHAR);

            Timestamp orderDateTs = orderHeader.getTimestamp("orderDate");
            String orderDate = MpReportUtil.getStringDateFromTimestamp(orderDateTs);

            String orderProductPromoCode = MpReportUtil.getOrderProductPromoCode(orderId, delegator);

            Map<String, String> resultMap = MpReportUtil.getCustomerInformation(orderId, delegator);

            StringBuilder reportRecord = new StringBuilder();

            if (!resultMap.isEmpty()) {
                reportRecord.append(resultMap.get("registrationDate")).append(";");
                reportRecord.append(resultMap.get("email")).append(";");
                reportRecord.append(resultMap.get("firstName")).append(";");
                reportRecord.append(resultMap.get("lastName")).append(";");
                reportRecord.append(resultMap.get("city")).append(";");
                reportRecord.append(resultMap.get("province")).append(";");
                reportRecord.append(resultMap.get("nation")).append(";");
            }

            reportRecord.append(orderId).append(";");
            reportRecord.append(orderDate).append(";");
            reportRecord.append(orderGrandTotalStr).append(";");
            reportRecord.append(orderProductPromoCode).append(";");

            //System.out.println("reportRecord.toString(): " + reportRecord.toString());

            //write the file
            try {
                if (writer != null) {
                    writer.append(reportRecord.toString());
                    writer.append(System.lineSeparator());
                }
            } catch (IOException ex) {
                Debug.logError(ex, module);
            }

            reportRecord = null;

        }

        //Write to the file
        try {
            writer.close();
        } catch (IOException ex) {
            Debug.logError(ex, module);
        }

        //Sending report by email
        boolean emailSent = false;

        String emailObject = tenantId + " - Customer Order Report";

        if (UtilValidate.isEmpty(mailCcAddress)) {

            emailSent = MpEmailServices.sendMailWithAttachment(outFilePath, outfilename, mailFromAddress, mailToAddress, null, null, emailObject, "text/plain", username, password, dispatcher);

        } else {

            //sendCC
            emailSent = MpEmailServices.sendMailWithAttachment(outFilePath, outfilename, mailFromAddress, mailToAddress, mailCcAddress, null, emailObject, "text/plain", username, password, dispatcher);
        }

        return ServiceUtil.returnSuccess("Customer Order Report successfully created. Mail Sent? " + emailSent);

    }

}//end class
