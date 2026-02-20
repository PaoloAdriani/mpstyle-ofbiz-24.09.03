package importer

import mpstyle.util.email.MpEmailServices
import org.apache.ofbiz.entity.util.EntityUtilProperties
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.base.util.UtilDateTime
import mpstyle.util.file.MpFileUtil



/*
Elaborazione file xml product
 */
def elaborateXMLFileList() {

    def method = "elaborateXMLFileList"

    String path 		= parameters.toreadpath
    String historyPath 	= parameters.historytowritepath
    String username 	= parameters.username
    String password 	= parameters.password
    String filenamepath = parameters.filenamepath
    Integer timeout 	= Integer.parseInt(parameters.timeout)

    def SYSTEM_RESOURCE_ID = "mpreport"

    Date nowDate = UtilDateTime.nowDate()
    String nowDateString = UtilDateTime.toDateTimeString(nowDate)

    def resultMap = [:]
    def importedFiles = ""
    def notImportedFiles = ""
    def status = ""

    //Retrieve email parameters
    String mailFromAddress = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.mail.fromAddress",null, delegator)
    String mailToAddress = EntityUtilProperties.getPropertyValue(SYSTEM_RESOURCE_ID, "report.mail.toAddress",null, delegator)

    List<String> filesFromFolderWP = MpFileUtil.readWithPath(path, filenamepath)

    if(!filesFromFolderWP || filesFromFolderWP.isEmpty()) {
        resultMap.messages = "File list is null or empty. Nothing to process, exit."
        return resultMap
    }

    def totalFileCount = filesFromFolderWP.size()
    def importedFileCount = 0
    def notImportedFileCount = 0

    def importFileResultMap = [:]

    for(String filename : filesFromFolderWP) {

        logInfo("Processing file => " + filename, method)

        importFileResultMap  = MpFileUtil.importFile(filename.trim(), historyPath.trim(), username, password,timeout, nowDateString, dispatcher)

        if (!ServiceUtil.isSuccess(importFileResultMap)) {
            def errorMsg = ServiceUtil.getErrorMessage(importFileResultMap)
            logError(errorMsg, method)
            notImportedFiles = notImportedFiles + filename.substring(filename.lastIndexOf("/") + 1) + ","
            notImportedFileCount++
            continue
        } else {
            importedFiles = importedFiles + filename.substring(filename.lastIndexOf("/") + 1) + ","
            importedFileCount++
        }

    }

    if (totalFileCount == importedFileCount) {
        status = "IMPORT SUCCESSFULL"
    } else if (totalFileCount == notImportedFileCount) {
        status = "IMPORT FAILED"
    } else {
        status = "IMPORT PARTIALLY SUCCESSFULL"
    }

    //Send a notification email with the status of the import
    if(filesFromFolderWP.size() > 0) {
        String bodyMsg = "Service: XMLEntityImporterMPS \n Service Status: " + status
        "\n Not Imported Files: [" + notImportedFiles + "] \n Run Time: " + nowDateString

        def mailSubject = delegator.getDelegatorTenantId() + " | XML Importer Service Status"

        boolean sent = MpEmailServices.sendSimpleMail(bodyMsg, mailFromAddress, mailToAddress, null, null, mailSubject,"text/plain", username, password, dispatcher)

        resultMap.messages = "Status: " + status + " - Imported files [" + importedFiles + "] - Not imported files [" + notImportedFiles + "] - Run Time [" + nowDateString + "] - Email sent: " + sent
    } else {
        resultMap.messages = "No files processed for import"
    }

    return resultMap

} //end elaborateXMLFileList

/*
def sendServiceStatusEmail(msg, username, password) {

    //Get tenant id
    String tenantId = delegator.getDelegatorTenantId();

    //Body
    String bodyString = msg;

    //Subject
    String subject = "XML Importer Service Status";

    if(tenantId == "GENTRY") {

        subject = "GENTRY - " + subject;

    }else if(tenantId == "ANGFASHION") {

        subject = "ANGFASHION - " + subject;

    } else if(tenantId == "CGPROD") {

        subject = "CGPROD - " + subject;

    } else if(tenantId == "FITEST") {

        subject = "FISICO - " + subject;

    } else if(tenantId == "DSMTEST") {

        subject = "DSMTEST - " + subject;

    }

//Get the partyId associated to the username ,to store the communication event
    GenericValue userLogin = delegator.findOne("UserLogin", UtilMisc.toMap("userLoginId", username), false);

    String commEventPartyId = "";

    if(userLogin != null) {
        commEventPartyId = userLogin.partyId;
    }

    Map<String, Object> mailIN = new HashMap();
    mailIN.put("body", bodyString);
    mailIN.put("sendFrom", "psg@mpstyle.info");
    mailIN.put("sendTo","giulio.speri@mpstyle.it");
    mailIN.put("sendCc", "nicola.mazzoni@mpstyle.it");
    mailIN.put("subject", subject);
    mailIN.put("login.username", username);
    mailIN.put("login.password", password);

    if(commEventPartyId != null && commEventPartyId != "") {
        mailIN.put("partyId", commEventPartyId);
    }

    Map<String, Object> mailOUT = runService("sendMail", mailIN);

}*/