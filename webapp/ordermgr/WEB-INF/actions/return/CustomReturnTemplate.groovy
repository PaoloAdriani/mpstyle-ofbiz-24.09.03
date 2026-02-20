/*
 * MpStyle srl - 28/02/2022
 * Check if exist custom invoice templates for a specific tenant (customer): if 
 * yes use that for rendering, otherwise use the standard template.
 *
 */

import org.ofbiz.base.util.*
import org.ofbiz.entity.util.EntityUtilProperties;


module = "CustomReturnTemplate.groovy"

systemResourceId = "mpreturn"

println "Searching for custom return templates (" + module + ")"

/* Searching for custom templates */
customCompanyLogoScreen = EntityUtilProperties.getSystemPropertyValue(systemResourceId, 'report.return.screencompanylogo', delegator);
customReturnHeader = EntityUtilProperties.getSystemPropertyValue(systemResourceId, 'report.return.headerinfo', delegator);
customReturnReportContactMech = EntityUtilProperties.getSystemPropertyValue(systemResourceId, 'report.return.contactmech', delegator);
customReturnReportBody = EntityUtilProperties.getSystemPropertyValue(systemResourceId, 'report.return.body', delegator);

/* Company Logo */
if(customCompanyLogoScreen && customCompanyLogoScreen != null && UtilValidate.isNotEmpty(customCompanyLogoScreen.trim())) {
    customCompanyLogoScreen = customCompanyLogoScreen.trim()
    println "found custom screen for company logo at this path => " + customCompanyLogoScreen
    context.customCompanyLogoScreen = customCompanyLogoScreen
}

/* Header Infos */
if(customReturnHeader && customReturnHeader != null && UtilValidate.isNotEmpty(customReturnHeader.trim())) {
    customReturnHeader = customReturnHeader.trim()
    println "found custom template for return header at this path => " + customReturnHeader
    context.customReturnHeader = customReturnHeader
}

/* Contact mech */
if(customReturnReportContactMech && customReturnReportContactMech != null && UtilValidate.isNotEmpty(customReturnReportContactMech.trim())) {
    customReturnReportContactMech = customReturnReportContactMech.trim()
    println "found custom template for contact mech at this path => " + customReturnReportContactMech
    context.customReturnReportContactMech = customReturnReportContactMech
}

/* Invoice Items */
if(customReturnReportBody && customReturnReportBody != null && UtilValidate.isNotEmpty(customReturnReportBody.trim())) {
    customReturnReportBody = customReturnReportBody.trim()
    println "found custom template for items at this path => " + customReturnReportBody
    context.customReturnReportBody = customReturnReportBody
}



