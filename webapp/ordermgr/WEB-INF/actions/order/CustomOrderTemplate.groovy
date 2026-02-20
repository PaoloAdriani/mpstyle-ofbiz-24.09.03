/*
 * MpStyle srl - 28/02/2022
 * Check if exist custom invoice templates for a specific tenant (customer): if 
 * yes use that for rendering, otherwise use the standard template.
 *
 */

import org.ofbiz.base.util.*
import org.ofbiz.entity.util.EntityUtilProperties;


module = "CustomOrderTemplate.groovy"

systemResourceId = "mporder"

println "Searching for custom order templates (" + module + ")"

/* Searching for custom templates */
customCompanyLogoScreen = EntityUtilProperties.getSystemPropertyValue(systemResourceId, 'report.order.screencompanylogo', delegator);
customOrderHeader = EntityUtilProperties.getSystemPropertyValue(systemResourceId, 'report.order.headerinfo', delegator);
customOrderReportContactMech = EntityUtilProperties.getSystemPropertyValue(systemResourceId, 'report.order.contactmech', delegator);
customOrderReportBody = EntityUtilProperties.getSystemPropertyValue(systemResourceId, 'report.order.body', delegator);

/* Company Logo */
if(customCompanyLogoScreen && customCompanyLogoScreen != null && UtilValidate.isNotEmpty(customCompanyLogoScreen.trim())) {
    customCompanyLogoScreen = customCompanyLogoScreen.trim()
    println "found custom screen for company logo at this path => " + customCompanyLogoScreen
    context.customCompanyLogoScreen = customCompanyLogoScreen
}

/* Header Infos */
if(customOrderHeader && customOrderHeader != null && UtilValidate.isNotEmpty(customOrderHeader.trim())) {
    customOrderHeader = customOrderHeader.trim()
    println "found custom template for order header at this path => " + customOrderHeader
    context.customOrderHeader = customOrderHeader
}

/* Contact mech */
if(customOrderReportContactMech && customOrderReportContactMech != null && UtilValidate.isNotEmpty(customOrderReportContactMech.trim())) {
    customOrderReportContactMech = customOrderReportContactMech.trim()
    println "found custom template for contact mech at this path => " + customOrderReportContactMech
    context.customOrderReportContactMech = customOrderReportContactMech
}

/* Invoice Items */
if(customOrderReportBody && customOrderReportBody != null && UtilValidate.isNotEmpty(customOrderReportBody.trim())) {
    customOrderReportBody = customOrderReportBody.trim()
    println "found custom template for items at this path => " + customOrderReportBody
    context.customOrderReportBody = customOrderReportBody
}



