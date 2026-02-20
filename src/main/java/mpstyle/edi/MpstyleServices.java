package mpstyle.edi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import mpstyle.edi.entity.Order;
import mpstyle.edi.entity.Return;
import mpstyle.log.MpStyleLogger;
import mpstyle.util.MpStyleUtil;
import mpstyle.util.fixedlength.FormattaValori;
import org.apache.ofbiz.accounting.invoice.InvoiceWorker;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.order.order.OrderReadHelper;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;



public class MpstyleServices {
	public static final String module = MpstyleServices.class.getName();

	private static final double PCT = 122;
	private static final double ONE_HUNDRED = 100;
	private static final String SHIPGROUPSEQID = "00001";
	private static final String SYSTEM_RESOURCE_ID = "mpstyle";

	public Map<String, Object> createCustomerOrderFileForErp(DispatchContext ctx,
			Map<String, ? extends Object> context) {

		final String IFLAC = "A";
		final String ITIPQT = "R";
		final String ICODAZ = "LI";
		final String ITIPDC = "F";

		final String LCIPREFIX = "SOL";
		final String SCIPREFIX = "SCFT";
		final String ECIPREFIX = "EC";

		boolean useCustomLogger = true;

		Delegator delegator = ctx.getDelegator();

		String tenantId = delegator.getDelegatorTenantId();

		String outPath = (String) context.get("outPath");
		String historyPath = (String) context.get("historyPath");
		String facilityCode = (String) context.get("facilityCode");
		String useBarcode = (String) context.get("useBarcode");

		List<Order> mpOrderList = new ArrayList<Order>();

		String orderFileName = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "orderCsv.fileName",
				"Ordini.csv", delegator);

		String logfilename = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "orderCsv.logfilename",
				delegator);

		String logdirpath = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "orderCsv.logdirpath", delegator);

		String codClPrefix = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "orderCsv.contactMechPrefix",
				"SOL", delegator);

		String iFlBulk = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "orderCsv.isBulk",delegator);	

		// Creation of the custom logger file
		MpStyleLogger logger = null;

		if (logfilename == null || UtilValidate.isEmpty(logfilename.trim()) || logdirpath == null
				|| UtilValidate.isEmpty(logdirpath.trim())) {
			Debug.logWarning(
					"Missing system properties [orderCsv.logfilename] and [orderCsv.logdirpath]. Cannot use custom logger file. Using standard only.",
					module);
			useCustomLogger = false;
		} else {
			logger = new MpStyleLogger(tenantId, logfilename.trim(), logdirpath.trim());
			useCustomLogger = true;
		}

		if (!outPath.endsWith("/")) {
			outPath = outPath + "/";
		}

		if (!historyPath.endsWith("/")) {
			historyPath = historyPath + "/";
		}

		if(iFlBulk.isEmpty())
		{
			iFlBulk="";
		}


		if (useCustomLogger)
			logger.logInfo("******** START (createCustomerOrderFileForErp) - Time: "
					+ MpStyleUtil.getNowDateTimeString() + " ********\n");

		List<GenericValue> orderHeaderList = findAllGenericValueList(delegator, "OrderHeader",
				UtilMisc.toMap("mpIsExportedMpsAcct", null, "statusId", "ORDER_COMPLETED"));

		if (orderHeaderList != null && !orderHeaderList.isEmpty()) {
			int swOrdini = 0;

			String iFlAc = IFLAC; // articolo attivo
			String iTipQt = ITIPQT; // tipo qualità regolare
			String iCodAz = ICODAZ;
			String iTipDc = ITIPDC; // fattura
			String iFisCl = "";
			String idClie = "";
			String iCodCC = "";
			String iFlSco = "";
			String iFlRin = "";

			for (GenericValue orderHeader : orderHeaderList) {

				String orderId = (String) orderHeader.get("orderId");

				if (useCustomLogger)
					logger.logInfo("******** OrderId : " + orderId + " ********\n");

				OrderReadHelper orh = new OrderReadHelper(delegator, orderId);
				
				GenericValue productStore = orh.getProductStore();

				String orderPrefix = productStore.getString("orderNumberPrefix");
				
				String iNumOr = orderId.substring(orderPrefix.length());
				
				//String iNumOr = orderId.substring(3); // iNumOr
				
				String iImpOtString = null;

				String iIvaTtString = null;

				List<GenericValue> orderAdjustmentList = findAllGenericValueList(delegator, "OrderAdjustment",
						UtilMisc.toMap("orderId", orderId, "orderAdjustmentTypeId", "PROMOTION_ADJUSTMENT"));

				boolean promoFreeShipping = isActivePromoFreeShipping(orderAdjustmentList);

				if (useCustomLogger)
					logger.logInfo("******** Promo Free Shipping active? : " + promoFreeShipping + " ********\n");

				// shipping costs

				double shippingCost = 0.0;

				List<GenericValue> orderShipmentList = findAllGenericValueList(delegator, "OrderShipment",
						UtilMisc.toMap("orderId", orderId, "shipGroupSeqId", SHIPGROUPSEQID));

				if (orderShipmentList != null && !orderShipmentList.isEmpty()) {
					GenericValue orderShipment = EntityUtil.getFirst(orderShipmentList);

					GenericValue shipment = findOneGenericValue(delegator, "Shipment",
							UtilMisc.toMap("shipmentId", orderShipment.getString("shipmentId")));

					if (shipment != null && !shipment.isEmpty()) {
						BigDecimal estShipCost = shipment.getBigDecimal("estimatedShipCost");

						shippingCost = estShipCost.doubleValue();
					}
				}

				if (useCustomLogger)
					logger.logInfo("******** Shipping cost : " + shippingCost + " ********\n");

				// Ricerco CodiceCliente

				List<GenericValue> contactMechList = findAllGenericValueList(delegator, "OrderContactMech",
						UtilMisc.toMap("orderId", orderId, "contactMechPurposeTypeId", "SHIPPING_LOCATION"));

				GenericValue contactMech = EntityUtil.getFirst(contactMechList);

				String wCodCl = (String) contactMech.get("contactMechId");

				GenericValue placingCustomer = orh.getPlacingParty();

				String customerPartyId = placingCustomer.getString("partyId");

				GenericValue partyContactMechPurposeMail = null;

				List<GenericValue> partyContactMechPurposeMailList = findAllGenericValueList(delegator,
						"PartyContactMechPurpose",
						UtilMisc.toMap("partyId", customerPartyId, "contactMechPurposeTypeId", "ORDER_EMAIL"));

				List<GenericValue> partyContactMechPurposeMailFilteredList = EntityUtil
						.filterByDate(partyContactMechPurposeMailList);

				if (partyContactMechPurposeMailFilteredList != null
						&& !partyContactMechPurposeMailFilteredList.isEmpty()) {
					partyContactMechPurposeMail = EntityUtil.getFirst(partyContactMechPurposeMailFilteredList);
				}

				GenericValue contactMechCustomer = null;

				try {

					contactMechCustomer = partyContactMechPurposeMail.getRelatedOne("ContactMech", false);

				} catch (GenericEntityException e) {
					if (useCustomLogger) {
						logger.logError(
								"************* Error to retreive Contact Mech from Party Contact Mech Purpose Email."
										+ e.getStackTrace());
					}
				}

				GenericValue postalAddress = findOneGenericValue(delegator, "PostalAddress",
						UtilMisc.toMap("contactMechId", contactMech.getString("contactMechId")));

				List<GenericValue> partyContactMechPurposeBillList = findAllGenericValueList(delegator,
						"PartyContactMechPurpose",
						UtilMisc.toMap("partyId", customerPartyId, "contactMechPurposeTypeId", "BILLING_LOCATION"));

				List<GenericValue> partyContactMechPurposeBillFilteredList = EntityUtil
						.filterByDate(partyContactMechPurposeBillList);

				GenericValue billingAddress = null;

				if (partyContactMechPurposeBillFilteredList != null
						&& !partyContactMechPurposeBillFilteredList.isEmpty()) {
					billingAddress = EntityUtil.getFirst(partyContactMechPurposeBillFilteredList);
				}

				if (billingAddress == null) {
					billingAddress = postalAddress;
				}

				if (useCustomLogger) {
					logger.logInfo("************* BillingAddress: " + billingAddress.toString());
				}

				GenericValue person = findOneGenericValue(delegator, "Person",
						UtilMisc.toMap("partyId", customerPartyId));

				GenericValue geo = findOneGenericValue(delegator, "Geo",
						UtilMisc.toMap("geoId", billingAddress.getString("countryGeoId")));
				
				String iIndCl = (billingAddress.getString("address1") != null) ? billingAddress.getString("address1") : "";
				
				if(UtilValidate.isNotEmpty(iIndCl))
				{
					iIndCl = cleanString(iIndCl);
				}

				String iCarCl = null;
				String iPagCl = null;
				String iTrnCl = null;

				List<GenericValue> orderPaymentsList = findAllGenericValueList(delegator, "OrderPaymentPreference",
						UtilMisc.toMap("orderId", orderId));

				if (orderPaymentsList != null && !orderPaymentsList.isEmpty()) {
					for (GenericValue orderPayment : orderPaymentsList) {
						String orderPaymentMethodTypeId = orderPayment.getString("paymentMethodTypeId");

						if (!orderPaymentMethodTypeId.startsWith("EXT_")) {
							continue;
						}

						iCarCl = orderPaymentMethodTypeId;

						iPagCl = orderPaymentMethodTypeId;

						iTrnCl = getCustomerTransactionId(iPagCl, orderHeader);

						if(iTrnCl == null || iTrnCl.isEmpty())
						{
							iTrnCl = " ";
						}

					}

				}

				if (useCustomLogger)
					logger.logInfo("******** iCarCl : " + iCarCl + " ********\n");
				if (useCustomLogger)
					logger.logInfo("******** iPagCl : " + iPagCl + " ********\n");
				if (useCustomLogger)
					logger.logInfo("******** iTrnCl : " + iTrnCl + " ********\n");

				String lc_invoice_prefix = LCIPREFIX;
				String sc_invoice_prefix = SCIPREFIX;
				String ec_invoice_prefix = ECIPREFIX;

				int lcprefix_len = lc_invoice_prefix.length();
				int scprefix_len = sc_invoice_prefix.length();
				int ecprefix_len = ec_invoice_prefix.length();

				List<GenericValue> orderItemList = findAllGenericValueList(delegator, "OrderItem",
						UtilMisc.toMap("orderId", orderId, "statusId", "ITEM_COMPLETED"));

				String iNumDo = "";
				String iDatDo = "";
				String iCodDp = "";
				String iCodCl = "";

				for (GenericValue orderItem : orderItemList) {

					logger.logInfo("***** orderItem: " + orderItem);

					List<GenericValue> orderItemBillingList = findAllGenericValueList(delegator, "OrderItemBilling",
							UtilMisc.toMap("orderId", orderId, "orderItemSeqId",
									orderItem.getString("orderItemSeqId")));

					GenericValue orderItemBilling = EntityUtil.getFirst(orderItemBillingList);

					logger.logInfo("***************** orderItemBilling: " + orderItemBilling);

					String invoiceId = (String) orderItemBilling.get("invoiceId");

					logger.logInfo("************ invoiceId: " + invoiceId);

					if (invoiceId.startsWith(lc_invoice_prefix)) 
					{
						int pref_idx = invoiceId.indexOf(lc_invoice_prefix);

						logger.logInfo("********* pref_idx: " + pref_idx);

						iNumDo = invoiceId.substring(pref_idx + lcprefix_len);

						logger.logInfo("****************** iNumDo: " + iNumDo);

						if (iNumDo.length() > 9) 
						{
							iNumDo = iNumDo.substring(2);

							logger.logInfo("******************* iNumDo substring(2): " + iNumDo);
						}

					} else if (invoiceId.startsWith(sc_invoice_prefix)) { // SC

						int pref_idx = invoiceId.indexOf(sc_invoice_prefix);
						iNumDo = invoiceId.substring(pref_idx + scprefix_len);

						if (iNumDo.length() > 9) {
							iNumDo = iNumDo.substring(2); // remove first 2 digits from year
						}

					} else if (invoiceId.startsWith(ec_invoice_prefix)) { // EC

						int pref_idx = invoiceId.indexOf(ec_invoice_prefix);
						iNumDo = invoiceId.substring(pref_idx + ecprefix_len);

						if (iNumDo.length() > 9) {
							iNumDo = iNumDo.substring(2); // remove first 2 digits from year
						}

					}

					GenericValue invoice = findOneGenericValue(delegator, "Invoice",
							UtilMisc.toMap("invoiceId", invoiceId));

					logger.logInfo("******************* invoice: " + invoice);		

					Timestamp paidDateInvoice = invoice.getTimestamp("paidDate");

					logger.logInfo("***************** paidDateInvoice: " + paidDateInvoice);		

					if (paidDateInvoice != null && !UtilValidate.isEmpty(paidDateInvoice)) {

						String invoiceDateString = paidDateInvoice.toString();

						logger.logInfo("************* invoiceDateString: " + invoiceDateString);		

						String year = invoiceDateString.substring(0, 4);

						logger.logInfo("************ year: " + year);

						String month = invoiceDateString.substring(5, 7);

						logger.logInfo("************* month: " + month);

						String day = invoiceDateString.substring(8, 10);

						logger.logInfo("************** day: " + day);

						iDatDo = year + month + day;

						logger.logInfo("********************** iDatDo from paidDateInvoice: " + iDatDo);

					} else {

						List<GenericValue> listStatus = orh.getOrderStatuses();

						logger.logInfo("********* listStatus: " + listStatus);

						for (GenericValue status : listStatus) {
							if (status.getString("statusId").equals("ORDER_APPROVED")) {
								if (status.getTimestamp("statusDatetime") != null) {
									Timestamp statusDateTimeInvoice = status.getTimestamp("statusDatetime");

									if (useCustomLogger) {
										logger.logInfo("************* statusDateTimeInvoice into order_approved: "
												+ statusDateTimeInvoice);
									}

									String invoiceDateTime = statusDateTimeInvoice.toString();

									String year = invoiceDateTime.substring(0, 4);
									String month = invoiceDateTime.substring(5, 7);
									String day = invoiceDateTime.substring(8, 10);
									iDatDo = year + month + day;
									
									logger.logInfo("********************iDatDo from statusDateTimeInvoice approved order: " + iDatDo);

								} else {

									if (status.getString("statusId").equals("ORDER_COMPLETED")) {
										Timestamp statusDateTimeInvoice = status.getTimestamp("statusDatetime");

										if (useCustomLogger) {
											logger.logInfo("************* statusDateTimeInvoice into order_completed: "
													+ statusDateTimeInvoice);
										}

										String invoiceDateTime = statusDateTimeInvoice.toString();

										String year = invoiceDateTime.substring(0, 4);
										String month = invoiceDateTime.substring(5, 7);
										String day = invoiceDateTime.substring(8, 10);
										iDatDo = year + month + day;

										logger.logInfo("******************* iDatDo from completed order: " + iDatDo);

									}
								}
							}
						}
					}

					swOrdini = 1;

					String varProductId = orderItem.getString("productId");

					String iBarcode = "";

					String iCodSt = "";
					String iCodLn = "";
					String iCodAr = "";
					String iCodVc = "";
					String iDesTg = "";

					if ("N".equals(useBarcode)) {

						if (useCustomLogger)
							logger.logInfo("******** useBarcode : " + useBarcode + " ********\n");

						iCodSt = varProductId.substring(0, 2);
						iCodLn = varProductId.substring(2, 3);
						iCodAr = varProductId.substring(3, 9);
						//iCodVc = varProductId.substring(9, varProductId.indexOf("."));
						//iDesTg = varProductId.substring(varProductId.indexOf(".") + 1);
						
						List<GenericValue> productFeatureColor = findAllGenericValueList(delegator, "ProductFeatureAndAppl",
								UtilMisc.toMap("productId", varProductId, "productFeatureApplTypeId","STANDARD_FEATURE","productFeatureTypeId", "COLOR"));
						
						List<GenericValue> productFeatureSize = findAllGenericValueList(delegator, "ProductFeatureAndAppl",
								UtilMisc.toMap("productId", varProductId, "productFeatureApplTypeId","STANDARD_FEATURE","productFeatureTypeId", "SIZE"));
						
						GenericValue firstColor = null;
						GenericValue firstSize = null;
						 
						
						if(productFeatureColor != null && productFeatureColor.size() > 0)
						{
							firstColor = EntityUtil.getFirst(productFeatureColor);
							
							iCodVc = firstColor.getString("productFeatureId");
							
						}
						
						if(productFeatureSize != null && productFeatureSize.size() > 0)
						{
							firstSize = EntityUtil.getFirst(productFeatureSize);
							
							iDesTg = firstSize.getString("productFeatureId");
							
						}
						
						String seasonLine = iCodSt + iCodLn;
				        
				        String colorSize = iCodVc + "." + iDesTg;
				        
				        logger.logInfo("St: " + iCodSt + ", Ln: " + iCodLn + ", Colore: " + iCodVc + ", Size: " + iDesTg);
				        
				        logger.logInfo("color-size: " + colorSize);
				       
				        logger.logInfo(varProductId.length() + " - " + varProductId.indexOf(colorSize));

				        iCodAr = varProductId.substring(seasonLine.length(), varProductId.indexOf(colorSize));
				        
				        logger.logInfo(iCodAr);
						

					} else {

						if (useCustomLogger)
							logger.logInfo("******** useBarcode : " + useBarcode + " ********\n");

						// use Barcode instead of st/ln/art/col/tg
						iCodSt = "";
						iCodLn = "";
						iCodAr = "";
						iCodVc = "";
						iDesTg = "";

						// Get the Good Idnetification number of the item (if exists)

						GenericValue variantProduct = findOneGenericValue(delegator, "Product",
								UtilMisc.toMap("productId", varProductId));

						List<GenericValue> goodIdentificationList = null;
						try {
							goodIdentificationList = variantProduct.getRelated("GoodIdentification",
									UtilMisc.toMap("productId", varProductId, "goodIdentificationTypeId", "EAN"), null,
									false);
						} catch (GenericEntityException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						if (goodIdentificationList != null && goodIdentificationList.size() > 0) {
							GenericValue goodIdentificationNum = EntityUtil.getFirst(goodIdentificationList);
							iBarcode = goodIdentificationNum.getString("idValue");

							if (useCustomLogger)
								logger.logInfo("******** iBarcode : " + iBarcode + " ********\n");
						}
					}

					String iQtaVn = FormattaValori.Arrotonda(orderItem.getBigDecimal("quantity").toString()); // quantity

					iCodDp = facilityCode;

					iCodCl = codClPrefix + wCodCl;

					BigDecimal invoiceTotal = InvoiceWorker.getInvoiceTotal(invoice);

					logger.logInfo("************************************invoiceTotal: " + invoiceTotal);

					double invTotDouble = invoiceTotal.doubleValue();

					double inv_val = getIva(invTotDouble, PCT);

					iImpOtString = FormattaValori.Arrotonda(String.valueOf(inv_val));

					double invval_noiva = invTotDouble - inv_val;

					iIvaTtString = FormattaValori.Arrotonda(String.valueOf(invval_noiva));

					List<GenericValue> orderItemAdjustmentList = findAllGenericValueList(delegator, "OrderAdjustment",
							UtilMisc.toMap("orderId", orderId, "orderItemSeqId", orderItem.getString("orderItemSeqId"),
									"orderAdjustmentTypeId", "PROMOTION_ADJUSTMENT"));

					BigDecimal amount = BigDecimal.ZERO;

					if (orderItemAdjustmentList != null && !orderItemAdjustmentList.isEmpty()) {
						for (GenericValue orderItemAdjustment : orderItemAdjustmentList) {
							amount = amount.add(orderItemAdjustment.getBigDecimal("amount"));
						}
					}

					BigDecimal iPrzVnNetto = orderItem.getBigDecimal("unitPrice").add(amount);

					double iPrzVnDouble = iPrzVnNetto.doubleValue();

					String iPrzVn = FormattaValori.Arrotonda(String.valueOf(iPrzVnDouble));

					double iImpVnDouble = getIva(iPrzVnDouble, PCT);

					String iImpVn = FormattaValori.Arrotonda(String.valueOf(iImpVnDouble));

					double iImpIvDouble = iPrzVnDouble - iImpVnDouble;

					String iImpIv = FormattaValori.Arrotonda(String.valueOf(iImpIvDouble));

					if (useCustomLogger)
						logger.logInfo("******** Creation mpOrder object ********\n");

					Order mpOrder = new Order();

					mpOrder.setiFlAc(iFlAc);
					mpOrder.setiCodSt(iCodSt);
					mpOrder.setiCodLn(iCodLn);
					mpOrder.setiCodAr(iCodAr);
					mpOrder.setiCodVc(iCodVc);
					mpOrder.setiDesTg(iDesTg);
					mpOrder.setiTipDc(iTipDc);
					mpOrder.setiQtaVn(iQtaVn);
					mpOrder.setiPrzVn(iPrzVn);
					mpOrder.setiImpVn(iImpVn);
					mpOrder.setiImpIv(iImpIv);
					mpOrder.setiTipQt(iTipQt);
					mpOrder.setiCodAz(iCodAz);
					mpOrder.setiNumDo(iNumDo);
					mpOrder.setiDatDo(iDatDo);
					mpOrder.setiCodCl(iCodCl);
					mpOrder.setiNumOr(iNumOr);
					mpOrder.setiCogCl(person.getString("lastName"));
					mpOrder.setiNomCl(person.getString("firstName"));
					mpOrder.setiIndCl(iIndCl);
					mpOrder.setiCapCl(billingAddress.getString("postalCode"));
					mpOrder.setiCitCl(billingAddress.getString("city"));
					mpOrder.setiNazCl(geo.getString("geoCode"));
					mpOrder.setiFisCl(iFisCl);
					mpOrder.setiMaiCl(contactMechCustomer.getString("infoString"));
					mpOrder.setiPagCl(iPagCl);
					mpOrder.setiCarCl(iCarCl);
					mpOrder.setiTrnCl(iTrnCl);
					mpOrder.setIdClie(idClie);
					mpOrder.setiCodCC(iCodCC);
					mpOrder.setiImpOt(iImpOtString);
					mpOrder.setiIvaTt(iIvaTtString);
					mpOrder.setiFlRin(iFlRin);
					mpOrder.setiCodDp(iCodDp);
					mpOrder.setiFlSco(iFlSco);
					mpOrder.setiBarcode(iBarcode);
					mpOrder.setiFlBulk(iFlBulk);
					

					mpOrderList.add(mpOrder);

					if (useCustomLogger)
						logger.logInfo("******** mpOrder object: " + mpOrder.toString() + " ********\n");

					if (useCustomLogger)
						logger.logInfo("******** mpOrder object added into list ********\n");

					logger.logInfo(">>>>>>> mpOrder object: " + mpOrder.toString());

				}
				
				logger.logInfo(">>>>>>> Start Write shipping cost ");

				if (shippingCost != 0 && !promoFreeShipping) {

					if (useCustomLogger)
						logger.logInfo("******** Write shipping cost ********\n");

					String iCodSt = "";
					String iCodLn = "";
					String iCodAr = "";
					String iCodVc = "";
					String iDesTg = "";
					String iBarcode = "";
					String iFlBulkCost = "";

					String iQtaVn = "1";

					double val = shippingCost;

					String iPrzVnString = FormattaValori.Arrotonda(String.valueOf(val));

					double iImpVn = getIva(shippingCost, PCT);

					String iImpVnString = FormattaValori.Arrotonda(String.valueOf(iImpVn));

					double iImpIv = shippingCost - iImpVn;

					String iImpIvString = FormattaValori.Arrotonda(String.valueOf(iImpIv));

					// creare oggetto e scrivere su file

					Order mpOrder = new Order();

					mpOrder.setiFlAc(iFlAc);
					mpOrder.setiCodSt(iCodSt);
					mpOrder.setiCodLn(iCodLn);
					mpOrder.setiCodAr(iCodAr);
					mpOrder.setiCodVc(iCodVc);
					mpOrder.setiDesTg(iDesTg);
					mpOrder.setiTipDc(iTipDc);
					mpOrder.setiQtaVn(iQtaVn);
					mpOrder.setiPrzVn(iPrzVnString);
					mpOrder.setiImpVn(iImpVnString);
					mpOrder.setiImpIv(iImpIvString);
					mpOrder.setiTipQt(iTipQt);
					mpOrder.setiCodAz(iCodAz);
					mpOrder.setiNumDo(iNumDo);
					mpOrder.setiDatDo(iDatDo);
					mpOrder.setiCodCl(iCodCl);
					mpOrder.setiNumOr(iNumOr);
					mpOrder.setiCogCl(person.getString("lastName"));
					mpOrder.setiNomCl(person.getString("firstName"));
					mpOrder.setiIndCl(billingAddress.getString("address1"));
					mpOrder.setiCapCl(billingAddress.getString("postalCode"));
					mpOrder.setiCitCl(billingAddress.getString("city"));
					mpOrder.setiNazCl(geo.getString("geoCode"));
					mpOrder.setiFisCl(iFisCl);
					mpOrder.setiMaiCl(contactMechCustomer.getString("infoString"));
					mpOrder.setiPagCl(iPagCl);
					mpOrder.setiCarCl(iCarCl);
					mpOrder.setiTrnCl(iTrnCl);
					mpOrder.setIdClie(idClie);
					mpOrder.setiCodCC(iCodCC);
					mpOrder.setiImpOt(iImpOtString);
					mpOrder.setiIvaTt(iIvaTtString);
					mpOrder.setiFlRin(iFlRin);
					mpOrder.setiCodDp(iCodDp);
					mpOrder.setiFlSco(iFlSco);
					mpOrder.setiBarcode(iBarcode);
					mpOrder.setiFlBulk(iFlBulkCost);

					mpOrderList.add(mpOrder);

					if (useCustomLogger) {
						logger.logInfo("************* MpOrder Object with shipping cost: " + mpOrder.toString());
					}

				}

				logger.logInfo(">>>>>>> Start Update orderHeader: \n");
				
				orderHeader.setString("mpIsExportedMpsAcct", "Y");

				try {

					orderHeader.store();

				} catch (GenericEntityException e) {

					if (useCustomLogger) {
						logger.logError(
								"Error while uploading Order Header: " + e.getMessage() + " and " + e.getStackTrace());
					}

				}

			}
			
			logger.logInfo(">>>>>>> End Update orderHeader: \n");

			createOrderCsvFile(mpOrderList, outPath, orderFileName);

			if (swOrdini == 1) {
				copyFileIntoHistoryFolder(outPath, historyPath, orderFileName);
			}

		}

		if (useCustomLogger)
			logger.logInfo("******** END (createCustomerOrderFileForErp) - Time: " + MpStyleUtil.getNowDateTimeString()
					+ " ********\n");

		return ServiceUtil.returnSuccess();
	}

	public Map<String, Object> createCustomerReturnFileForErp(DispatchContext ctx, Map<String, ? extends Object> context) {
		
		boolean useCustomLogger = true;

		final String IFLAC = "A";
		final String ICODAZ = "LI";
		final String ITIPDC = "N";

		Delegator delegator = ctx.getDelegator();

		String tenantId = delegator.getDelegatorTenantId();

		String outPath = (String) context.get("outPath");
		String historyPath = (String) context.get("historyPath");
		String facilityCode = (String) context.get("facilityCode");
		String qualityType = (String) context.get("qualityType");

		List<Return> mpReturnList = new ArrayList<Return>();

		String RET_CMECH_PREFIX = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID,"returnCsv.contactMechPrefix", "SOL", delegator);

		String returnFileName = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "returnCsv.fileName","Resi.csv", delegator);

		String logfilename = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "returnCsv.logfilename",delegator);
		String logdirpath = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "returnCsv.logdirpath",delegator);

		// Creation of the custom logger file
		MpStyleLogger logger = null;

		if (logfilename == null || UtilValidate.isEmpty(logfilename.trim()) || logdirpath == null
				|| UtilValidate.isEmpty(logdirpath.trim())) {
			Debug.logWarning(
					"Missing system properties [returnCsv.logfilename] and [returnCsv.logdirpath]. Cannot use custom logger file. Using standard only.",
					module);
			useCustomLogger = false;
		} else {
			logger = new MpStyleLogger(tenantId, logfilename.trim(), logdirpath.trim());
			useCustomLogger = true;
		}

		String iFlAc = IFLAC;
		String iCodAz = ICODAZ;
		String iTipDc = ITIPDC;
		String iFisCl = "";
		String idClie = "";
		String iCodCC = "";
		String iFlRin = "";
		String iCodDp = "";
		String iNumOr = "";
		String iDatDo = "";
		String iNumDo = "";

		
		if (useCustomLogger)
			logger.logInfo("******** START (createCustomerReturnFileForErp) - Time: "
					+ MpStyleUtil.getNowDateTimeString() + " ********\n");
		
		
		List<GenericValue> returnHeaderList = findAllGenericValueList(delegator, "ReturnHeader",
				UtilMisc.toMap("mpIsExportedMpsAcct", "N", "statusId", "RETURN_COMPLETED"));

		if (returnHeaderList != null && !returnHeaderList.isEmpty()) 
		{
			
			
			for (GenericValue returnHeader : returnHeaderList) 
			{
				
				if (useCustomLogger)
					logger.logInfo("******** Return id: " + returnHeader.getString("returnId") + "********\n");
				
				
				iNumOr = FormattaValori.formattaDouble("00000", returnHeader.getString("returnId").toString());

				iNumDo = RET_CMECH_PREFIX + iNumOr;
				
				logger.logInfo(">>>>>>> iNumOr: " + iNumOr);

				String iTrnCl = "";

				String wCodCl = returnHeader.getString("fromPartyId");
				
				String originContactMechId = returnHeader.getString("originContactMechId");

				logger.logInfo(">>>>>>> wCodCl: " + wCodCl);
				
				List<GenericValue> returnStatusList = findAllGenericValueList(delegator, "ReturnStatus",UtilMisc.toMap("returnId", iNumOr, "statusId", "RETURN_COMPLETED"));

				String statusDateTime = null;

				if (returnStatusList != null && !returnStatusList.isEmpty()) 
				{
					GenericValue returnStatusResult = returnStatusList.get(0);
					
					Timestamp sdt = returnStatusResult.getTimestamp("statusDatetime");

					statusDateTime = sdt.toString();
					
					String invoiceDateString = statusDateTime.toString();
					String year = invoiceDateString.substring(0, 4);
					String month = invoiceDateString.substring(5, 7);
					String day = invoiceDateString.substring(8, 10);

					iDatDo = year + month + day;

					logger.logInfo(
							">>>>>>>>>>>>>>>>> iDatDo: " + iDatDo
							);

				}

				String returnMethod = null;

				//double grandTotal = 0;
				
				String iCarCl = "";
				String iPagCl = "";
				String iCogCl = "";
				String iNomCl = "";
				String iIndCl = "";
				String iCapCl = "";
				String iCitCl = "";
				String iMaiCl = "";
				String iNazCl = "";
				String iTipQt = "";
				String wFlSco = "";
				
				String orderIdFromReturn = null;
				
				List<Return> tmpResi = new ArrayList<Return>();
				
				List<GenericValue> returnItemList = findAllGenericValueList(delegator, "ReturnItem", UtilMisc.toMap("returnId", returnHeader.getString("returnId")));

				if (returnItemList != null && !returnItemList.isEmpty()) 
				{
					for (GenericValue returnItem : returnItemList) 
					{
						logger.logInfo("************* Retrieve fields *******\n");
						
						double grandTotalPrice = 0;
						
						returnMethod = (String) returnItem.get("mpReturnMethod");
						
						String returnItemSeqId = (String) returnItem.get("returnItemSeqId");

						orderIdFromReturn = returnItem.getString("orderId");

						OrderReadHelper orh = new OrderReadHelper(delegator, orderIdFromReturn);
						
						GenericValue billParty = orh.getBillToParty();

						GenericValue billingAddress = null;

						if (originContactMechId == null) 
						{
							billingAddress = orh.getShippingAddress(SHIPGROUPSEQID);

							if (useCustomLogger)
								logger.logInfo("******** Retrieve billing address from order read helper ********\n");

						} else {

							billingAddress = findOneGenericValue(delegator, "PostalAddress",UtilMisc.toMap("contactMechId", originContactMechId));
						}

						GenericValue geo = findOneGenericValue(delegator, "Geo",UtilMisc.toMap("geoId", billingAddress.getString("countryGeoId")));

						if (returnMethod.equals("RETAIL")) 
						{
							iCogCl = (billParty.getString("lastName") != null) ? billParty.getString("lastName") : "";

							iNomCl = (billParty.getString("firstName") != null) ? billParty.getString("firstName") : "";

							iMaiCl = (orh.getOrderEmailString() != null) ? orh.getOrderEmailString() : "";

							iNazCl = "";

						} else {

							logger.logInfo(">>>>>>> returnMethod: " + returnMethod);

							iCogCl = (billParty.getString("lastName") != null) ? billParty.getString("lastName") : "";

							iNomCl = (billParty.getString("firstName") != null) ? billParty.getString("firstName") : "";

							iIndCl = (billingAddress.getString("address1") != null) ? billingAddress.getString("address1") : "";
							
							if(UtilValidate.isNotEmpty(iIndCl))
							{
								iIndCl = cleanString(iIndCl);
							}

							iCapCl = (billingAddress.getString("postalCode") != null) ? billingAddress.getString("postalCode")
									: "";

							iCitCl = (billingAddress.getString("city") != null) ? billingAddress.getString("city") : "";

							iNazCl = (geo.getString("geoCode") != null) ? geo.getString("geoCode") : "";

							iMaiCl = (orh.getOrderEmailString() != null) ? orh.getOrderEmailString() : "";

						}
						
						String iCodSt = returnItem.getString("productId").substring(0, 2);

						String iCodLn = returnItem.getString("productId").substring(2, 3);

						String iCodAr = returnItem.getString("productId").substring(3, 9);

						String iCodVc = returnItem.getString("productId").substring(9,
								returnItem.getString("productId").indexOf("."));

						String iDesTg = returnItem.getString("productId")
								.substring(returnItem.getString("productId").indexOf(".") + 1);

						String iQtaVn = FormattaValori.Arrotonda(returnItem.getBigDecimal("returnQuantity").toString());

						iCodDp = facilityCode;

						String iCodCl = RET_CMECH_PREFIX + wCodCl;
						
						iTipQt = qualityType;

						String mpDepositoCarico = returnItem.getString("mpDepositoCarico");

						if (mpDepositoCarico != null && !mpDepositoCarico.isEmpty()) 
						{
							if (mpDepositoCarico.equals("MF")) 
							{
								iTipQt = "F";
							}

						}
						
						
						BigDecimal productPrice = returnItem.getBigDecimal("returnPrice");
						
						BigDecimal retAdjPromotion = orh.getReturnAdjustmentTotal(delegator, UtilMisc.toMap("returnAdjustmentTypeId","RET_PROMOTION_ADJ", "returnId", returnHeader.getString("returnId"), "returnItemSeqId",returnItemSeqId));
						
						logger.logInfo(
								">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> retAdjPromotion: "+retAdjPromotion);
						
						grandTotalPrice = Double.parseDouble(productPrice.toString())+ Double.parseDouble(retAdjPromotion.toString());

						String iPrzVn = FormattaValori.Arrotonda(String.valueOf(grandTotalPrice));

						double ivaPrzVn = getIva(grandTotalPrice, PCT);

						String iImpVn = FormattaValori.Arrotonda(String.valueOf(ivaPrzVn));

						double impIv = grandTotalPrice - ivaPrzVn;

						String iImpIv = FormattaValori.Arrotonda(String.valueOf(impIv));
						
						logger.logInfo("************* Create MpReturn Object: *******\n");
						
						Return mpReturn = new Return();

						mpReturn.setiFlAc(iFlAc);
						mpReturn.setiCodSt(iCodSt);
						mpReturn.setiCodLn(iCodLn);
						mpReturn.setiCodAr(iCodAr);
						mpReturn.setiCodVc(iCodVc);
						mpReturn.setiDesTg(iDesTg);
						mpReturn.setiTipDc(iTipDc);
						mpReturn.setiQtaVn(iQtaVn);
						mpReturn.setiPrzVn(iPrzVn);
						mpReturn.setiImpVn(iImpVn);
						mpReturn.setiImpIv(iImpIv);
						mpReturn.setiTipQt(iTipQt);
						mpReturn.setiCodAz(iCodAz);
						mpReturn.setiNumDo(iNumDo);
						mpReturn.setiDatDo(iDatDo);
						mpReturn.setiCodCl(iCodCl);
						mpReturn.setiNumOr(iNumOr);
						mpReturn.setiCogCl(iCogCl);
						mpReturn.setiNomCl(iNomCl);
						mpReturn.setiIndCl(iIndCl);
						mpReturn.setiCapCl(iCapCl);
						mpReturn.setiCitCl(iCitCl);
						mpReturn.setiNazCl(iNazCl);
						mpReturn.setiFisCl(iFisCl);
						mpReturn.setiMaiCl(iMaiCl);
						mpReturn.setiPagCl(iPagCl);
						mpReturn.setiCarCl(iCarCl);
						mpReturn.setiTrnCl(iTrnCl);
						mpReturn.setIdClie(idClie);
						mpReturn.setiCodCC(iCodCC);
						mpReturn.setiFlRin(iFlRin);
						mpReturn.setiCodDp(iCodDp);
						mpReturn.setwFlSco(wFlSco);
						
						if (useCustomLogger) {

							logger.logInfo("************* MpReturn Object: " + mpReturn.toString() + " *******\n");
						}
						
						tmpResi.add(mpReturn);
						
					}
				}
				
				logger.logInfo("************* Retrieve other adjustment *******\n");
				
				OrderReadHelper orderReadHelper = new OrderReadHelper(delegator, orderIdFromReturn);
				
				BigDecimal orderReturnedTotal = orderReadHelper.getOrderReturnedTotalByTypeBd("RTN_REFUND", true);
				
				logger.logInfo("************* orderReturnedTotal:  *******"+orderReturnedTotal);
				
				BigDecimal retAdjShippingTotal = orderReadHelper.getReturnAdjustmentTotal(delegator, UtilMisc.toMap("returnAdjustmentTypeId","RET_SHIPPING_ADJ", "returnId", returnHeader.getString("returnId")));
				
				logger.logInfo("************* retAdjShippingTotal *******:"+retAdjShippingTotal);
				
				double iva = getIva(orderReturnedTotal.doubleValue(), PCT);

				String iImpOt = FormattaValori.Arrotonda(String.valueOf(iva));
				
				logger.logInfo("************* iImpOt:  *******"+iImpOt);

				double iIvaTtDouble = orderReturnedTotal.doubleValue() - iva;

				String iIvaTt = FormattaValori.Arrotonda(String.valueOf(iIvaTtDouble));
				
				logger.logInfo("************* iIvaTt:  *******"+iIvaTt);
				
				logger.logInfo("************* Create shipping cost row (if exists) *******\n");
				
				if (retAdjShippingTotal != null && retAdjShippingTotal.compareTo(BigDecimal.ZERO) != 0)
				{
					String iCodSt = "";
					String iCodLn = "";
					String iCodAr = "";
					String iCodVc = "";
					String iDesTg = "";
					String wFlScoSco = "S";

					String iQtaVn = "1";
					
					String iCodCl = RET_CMECH_PREFIX + wCodCl;

					double val = retAdjShippingTotal.abs().doubleValue();
					
					String iPrzVnString = FormattaValori.Arrotonda(String.valueOf(val));

					double iImpVn = getIva(retAdjShippingTotal.abs().doubleValue(), PCT);

					String iImpVnString = FormattaValori.Arrotonda(String.valueOf(iImpVn));

					double iImpIv = retAdjShippingTotal.abs().doubleValue() - iImpVn;

					String iImpIvString = FormattaValori.Arrotonda(String.valueOf(iImpIv));

					Return mpReturn = new Return();

					mpReturn.setiFlAc(iFlAc);
					mpReturn.setiCodSt(iCodSt);
					mpReturn.setiCodLn(iCodLn);
					mpReturn.setiCodAr(iCodAr);
					mpReturn.setiCodVc(iCodVc);
					mpReturn.setiDesTg(iDesTg);
					mpReturn.setiTipDc(iTipDc);
					mpReturn.setiQtaVn(iQtaVn);
					mpReturn.setiPrzVn(iPrzVnString);
					mpReturn.setiImpVn(iImpVnString);
					mpReturn.setiImpIv(iImpIvString);
					mpReturn.setiTipQt(iTipQt);
					mpReturn.setiCodAz(iCodAz);
					mpReturn.setiNumDo(iNumDo);
					mpReturn.setiDatDo(iDatDo);
					mpReturn.setiCodCl(iCodCl);
					mpReturn.setiNumOr(iNumOr);
					mpReturn.setiCogCl(iCogCl);
					mpReturn.setiNomCl(iNomCl);
					mpReturn.setiIndCl(iIndCl);
					mpReturn.setiCapCl(iCapCl);
					mpReturn.setiCitCl(iCitCl);
					mpReturn.setiNazCl(iNazCl);
					mpReturn.setiFisCl(iFisCl);
					mpReturn.setiMaiCl(iMaiCl);
					mpReturn.setiPagCl(iPagCl);
					mpReturn.setiCarCl(iCarCl);
					mpReturn.setiTrnCl(iTrnCl);
					mpReturn.setIdClie(idClie);
					mpReturn.setiCodCC(iCodCC);
					mpReturn.setiFlRin(iFlRin);
					mpReturn.setiCodDp(iCodDp);
					mpReturn.setwFlSco(wFlScoSco);

					if (useCustomLogger) {

						logger.logInfo(
								"************* MpReturn Object shipping cost: " + mpReturn.toString() + " *******\n");
					}

					tmpResi.add(mpReturn);

				}

				for(Return reso : tmpResi)
				{
					reso.setiImpOt(iImpOt);
					reso.setiIvaTt(iIvaTt);
				
					logger.logInfo("************* MpReturn Object update: " + reso.toString() + " *******\n");
				
				}
				
				mpReturnList.addAll(tmpResi);
				
				logger.logInfo("************* Update return header *******\n");

				// aggiornamento testata resi
				
				returnHeader.setString("mpIsExportedMpsAcct", "Y");
				
				try {

					returnHeader.store();

				} catch (GenericEntityException e) {

					if (useCustomLogger) {

						logger.logError(
								"Error while uploading Return Header: " + e.getMessage() + " and " + e.getStackTrace());
					}

				}
				
			}
			
			logger.logInfo("************* Create return file csv and copy into history *******\n");
			
			createReturnCsvFile(mpReturnList, outPath, returnFileName);

			copyFileIntoHistoryFolder(outPath, historyPath, returnFileName);
			
		}

		if (useCustomLogger)
			logger.logInfo("******** END (createCustomerReturnFileForErp) - Time: " + MpStyleUtil.getNowDateTimeString()
					+ " ********\n");

		return ServiceUtil.returnSuccess();
	}

	private void copyFileIntoHistoryFolder(String outPath, String historyPath, String fileName) {
		
		File source = new File(outPath + fileName);
		
		File dest = new File(historyPath + UtilDateTime.nowDateString() + "_" + fileName);
		
		InputStream is = null;
		OutputStream os = null;
		try {
			is = new FileInputStream(source);
			os = new FileOutputStream(dest);
			byte[] buffer = new byte[1024];
			int length;
			while ((length = is.read(buffer)) > 0) {
				os.write(buffer, 0, length);
			}
		} catch (FileNotFoundException e) {
			Debug.logError(e, "File not present", module);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			Debug.logError(e, "Other error to file.", module);
		} finally {
			try {
				is.close();
				os.close();
			} catch (IOException e) {
				Debug.logError(e, "Other error to file.", module);
			}

		}
	}

	private List<GenericValue> findAllGenericValueList(Delegator delegator, String entityName,
			Map<String, Object> fieldValueMap) {
		List<GenericValue> result = null;

		try {

			result = delegator.findList(entityName, EntityCondition.makeCondition(fieldValueMap), null, null, null,
					false);

		} catch (GenericEntityException e) {// TODO Auto-generated catch block
			Debug.logError(e, "Error in retrieving values for entity[" + entityName + "]", module);

		}

		return result;
	}

	private boolean isActivePromoFreeShipping(List<GenericValue> orderAdjustmentList) {
		boolean result = false;

		if (orderAdjustmentList != null && !orderAdjustmentList.isEmpty()) {
			for (GenericValue orderAdjustment : orderAdjustmentList) {

				GenericValue productPromoAction = null;

				try {
					productPromoAction = orderAdjustment.getRelatedOne("ProductPromoAction", false);
				} catch (GenericEntityException e) {
					Debug.logError(e, "Error in retrieving values for entity[ProductPromoAction]", module);
				}

				if (productPromoAction != null) {
					String promoType = productPromoAction.getString("productPromoActionEnumId");

					BigDecimal amount = productPromoAction.getBigDecimal("amount");

					if (promoType.equals("PROMO_SHIP_CHARGE") && (amount.compareTo(new BigDecimal("100")) == 0)) {
						result = true;
					}
				}

			}
		}

		return result;
	}

	private GenericValue findOneGenericValue(Delegator delegator, String entityName,
			Map<String, Object> entityFieldValue) {
		GenericValue result = null;

		try {
			result = delegator.findOne(entityName, entityFieldValue, false);
		} catch (GenericEntityException e) {
			Debug.logError(e, "Error in retrieving value for entity[" + entityName + "]", module);
		}

		return result;
	}

	private double getIva(double grandTotal, double pct) {
		return (grandTotal / pct) * ONE_HUNDRED;
	}

	private String getCustomerTransactionId(String iPagCl, GenericValue orderHeader) {
		String result = null;

		Debug.logError("iPagCl[" + iPagCl + "]", module);

		switch (iPagCl) {

		case "EXT_PAYPAL":
			result = orderHeader.getString("mpPaymentId");
			break;

		case "EXT_UNICREDIT_CC":
			result = orderHeader.getString("mpTransId");
			break;

		case "EXT_UNICREDIT_MYBK":
			result = orderHeader.getString("mpTransId");
			break;

		case "EXT_worldline":
			result = orderHeader.getString("mpWorldLinePaymentId");
			break;

		case "EXT_scalapay":
			result = orderHeader.getString("mpScalapayOrderToken");
			break;

		}

		return result;

	}

	private void createOrderCsvFile(List<Order> mpOrderList, String outPath, String fileName) {

		File csvOutputFile = new File(outPath + fileName);
		
		if (!csvOutputFile.exists()) {
			try {
				csvOutputFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		CsvMapper mapper = new CsvMapper();

		mapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);

		CsvSchema schema = CsvSchema.builder().setUseHeader(false).setColumnSeparator(';')
				.addColumn("iFlAc")
				.addColumn("iCodSt")
				.addColumn("iCodLn")
				.addColumn("iCodAr")
				.addColumn("iCodVc")
				.addColumn("iDesTg")
				.addColumn("iTipDc")
				.addColumn("iQtaVn")
				.addColumn("iPrzVn")
				.addColumn("iImpVn")
				.addColumn("iImpIv")
				.addColumn("iTipQt")
				.addColumn("iCodAz")
				.addColumn("iNumDo")
				.addColumn("iDatDo")
				.addColumn("iCodCl")
				.addColumn("iNumOr")
				.addColumn("iCogCl")
				.addColumn("iNomCl")
				.addColumn("iIndCl")
				.addColumn("iCapCl")
				.addColumn("iCitCl")
				.addColumn("iNazCl")
				.addColumn("iFisCl")
				.addColumn("iMaiCl")
				.addColumn("iPagCl")
				.addColumn("iCarCl")
				.addColumn("iTrnCl")
				.addColumn("IdClie")
				.addColumn("iCodCC")
				.addColumn("iImpOt")
				.addColumn("iIvaTt")
				.addColumn("iFlRin")
				.addColumn("iCodDp")
				.addColumn("iFlSco")
				.addColumn("iBarcode")
				.addColumn("iFlBulk")
				.build().withoutQuoteChar().withNullValue("");

		ObjectWriter writer = mapper.writerFor(Order.class).with(schema);

		try {

			OutputStream outputStream = new FileOutputStream(csvOutputFile, true);

			writer.writeValues(outputStream).writeAll(mpOrderList);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Debug.logError(e, "Error into creating order file.", module);
		}

	}

	private void createReturnCsvFile(List<Return> mpReturnList, String outPath, String fileName) {

		File csvOutputFile = new File(outPath + fileName);

		if (!csvOutputFile.exists()) {
			try {
				csvOutputFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		CsvMapper mapper = new CsvMapper();

		mapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);

		CsvSchema schema = CsvSchema.builder().setUseHeader(false).setColumnSeparator(';').addColumn("iFlAc")
				.addColumn("iCodSt").addColumn("iCodLn").addColumn("iCodAr").addColumn("iCodVc").addColumn("iDesTg")
				.addColumn("iTipDc").addColumn("iQtaVn").addColumn("iPrzVn").addColumn("iImpVn").addColumn("iImpIv")
				.addColumn("iTipQt").addColumn("iCodAz").addColumn("iNumDo").addColumn("iDatDo").addColumn("iCodCl")
				.addColumn("iNumOr").addColumn("iCogCl").addColumn("iNomCl").addColumn("iIndCl").addColumn("iCapCl")
				.addColumn("iCitCl").addColumn("iNazCl").addColumn("iFisCl").addColumn("iMaiCl").addColumn("iPagCl")
				.addColumn("iCarCl").addColumn("iTrnCl").addColumn("idClie").addColumn("iCodCC").addColumn("iImpOt")
				.addColumn("iIvaTt").addColumn("iFlRin").addColumn("iCodDp").addColumn("wFlSco").build()
				.withoutQuoteChar();

		ObjectWriter writer = mapper.writerFor(Return.class).with(schema);

		try {

			OutputStream outputStream = new FileOutputStream(csvOutputFile, true);

			writer.writeValues(outputStream).writeAll(mpReturnList);
		} catch (IOException e) {
			Debug.logError(e, "Error into creating return file.", module);
		}

	}
	
	private String cleanString(String field)
    {
    	return (field != null) ? field.replaceAll("[^a-zA-Z0-9]", "") : "";
    }


}
