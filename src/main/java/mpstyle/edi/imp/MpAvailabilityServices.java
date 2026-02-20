/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.edi.imp;

import mpstyle.log.MpStyleLogger;
import mpstyle.util.MpStyleUtil;
import mpstyle.util.file.MpFileUtil;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.transaction.Transaction;
import javax.transaction.Status;


/**
 *
 * @author equake58
 */
public class MpAvailabilityServices {
    
    public static final String MODULE = MpAvailabilityServices.class.getName();
    
    private static final int INTERNAL_TRX_TIMEOUT_SEC = 600; 
    
    private static final String MPEDI_SYSTEM_RESOURCE_ID = "mpedi";
    
    /**
     * Service used to import ERP availabilities from a csv file with values 
     * separated by pipe operator ( | ), selecting products using barcodes.
     * A csv file record has the following structure:
     * OFBIZ_FACILITY_ID    |BARCODE_VALUE   |ERP_ATP_VALUE
     * @param dctx
     * @param context
     * @return 
     */
    public static Map<String, Object> importQOHFromBarcode(DispatchContext dctx, Map<String, Object> context) {
        
        final String METHOD = "importQOHFromBarcode";
        Map<String, Object> returnMap = null;
        
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = (LocalDispatcher) dctx.getDispatcher();
        
        boolean useCustomLogger = true;
        Map<String, String> fileErrorMap = new HashMap<>();
        Map<String, Integer> productCount = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> fileProductCount = new HashMap<>();
        List<String> errorList = new ArrayList<>();
        int successFileCount = 0;
        int errorFileCount = 0;
        int totalFilesProcessed = 0;
        boolean error = false;
        
        
        //Reading service input parameters
        String path = (String) context.get("toreadpath");
        String historyPath = (String) context.get("historytowritepath");
        String errorPath = (String) context.get("errortowritepath");
        String username = (String) context.get("username");
        String password = (String) context.get("password");
        String barcodeType = (String) context.get("barcodeType");
        
        String logfilename = EntityUtilProperties.getPropertyValue(MPEDI_SYSTEM_RESOURCE_ID, "ediimp.logfilename", delegator);
        String logdirpath = EntityUtilProperties.getPropertyValue(MPEDI_SYSTEM_RESOURCE_ID, "ediimp.logdirpath", delegator);
        
        /* Check on parameters and SystemPropeerties */
        
        //Creation of the custom logger file
        MpStyleLogger logger = null;
        
        if(logfilename == null  || UtilValidate.isEmpty(logfilename.trim()) || logdirpath == null || UtilValidate.isEmpty(logdirpath.trim())) {
            Debug.logWarning("Missing system properties [mpedi/ediimp.logfilename] and [mpedi/ediimp.logdirpath]. Cannot use custom logger file. Using standard only.", MODULE);
            useCustomLogger = false;
        }else{
            logger = new MpStyleLogger(delegator.getDelegatorTenantId(), logfilename.trim(), logdirpath.trim());
            useCustomLogger = true;
        }
        
        /* if service username and password are not set, then we cannot call services due to 
           auth problems. So do not perform the call and proceed anyway.
        */
        if(username == null || UtilValidate.isEmpty(username.trim()) || password == null || UtilValidate.isEmpty(password.trim())) {
            String msg = "*** Service username and/or password not found as service input parameter. Could not call services with authorization. Do not perform import availabilities.";
            if(useCustomLogger) {
                logger.logError(msg);
                logger.logInfo("******** END (" + METHOD + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logError(msg, MODULE);
            return ServiceUtil.returnError(msg);
        }
        
        username = username.trim();
        password = password.trim();
        
        if(useCustomLogger) logger.logInfo("******** START (" + METHOD + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
        
        Debug.logWarning("***** path: " + path, MODULE);
        Debug.logWarning("***** historyPath: " + historyPath, MODULE);
        Debug.logWarning("***** errorPath: " + errorPath, MODULE);
        Debug.logWarning("***** username: " + username, MODULE);
        Debug.logWarning("***** password: " + password, MODULE);
        Debug.logWarning("***** barcodeType: " + barcodeType, MODULE);
        
        
        List<String> filesFromFolder = MpFileUtil.readAndSort(path);
        
        //Keep only csv files
        filesFromFolder = MpFileUtil.filterFileNamesBySuffix(filesFromFolder, ".csv", true);
        //Keep only csv files starting with QOH_
        filesFromFolder = MpFileUtil.filterFileNamesByPrefix(filesFromFolder, "QOH_", true);
    
        if(filesFromFolder == null || UtilValidate.isEmpty(filesFromFolder)) {
            String msg = "No QOH files to process. Quit.";
            if(useCustomLogger) {
                logger.logInfo(msg);
                logger.logInfo("******** END (" + METHOD + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
            }
            Debug.logWarning(msg, MODULE);
            return ServiceUtil.returnSuccess("SUCCESS! No files to process.");
        }
        
        for(String filename : filesFromFolder) {
            
            if(useCustomLogger) logger.logInfo("Processing file: " + filename);
            Debug.logInfo("Processing filename: "+filename, MODULE);
            
            //Process each file in a separate transaction
            Transaction parent = null;
             
            try {
                 
                if (TransactionUtil.getStatus() != Status.STATUS_NO_TRANSACTION)
                {
                    parent = TransactionUtil.suspend();
                }
                
                boolean beganTransaction = true;
                
                try {
                    
                    beganTransaction = TransactionUtil.begin(INTERNAL_TRX_TIMEOUT_SEC);
                    
                    Map<String, Object> processReturnStatus = MpAvailabilityWorker.importERPAvailabilityByBarcode(filename, historyPath, errorPath, barcodeType, username, password, delegator, dispatcher);
                    
                    
                }catch(GenericTransactionException ex) {
                    TransactionUtil.rollback(beganTransaction, "Errore nella transazione. ne è già stata avviata una!", ex);
                }finally{
                    TransactionUtil.commit(beganTransaction);
                }
                 
                 
            }catch(GenericTransactionException e) {
                
                String transactionError = "Following transaction error occured while processing file ["+filename+"] => " + e.getMessage();
                if(useCustomLogger) {
                    logger.logError(transactionError);
                    logger.logInfo("******** END (" + METHOD + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
                }
                Debug.logError(transactionError, MODULE);

                return ServiceUtil.returnError(transactionError);
            
            }finally {
                if (parent != null) {
                    try {
                        TransactionUtil.resume(parent);

                    }catch(GenericTransactionException gte) {
                        String transactionError = "Error in resuming transaction. Error is => " + gte.getMessage(); 
                        if(useCustomLogger) {
                            logger.logError(transactionError);
                            logger.logInfo("******** END (" + METHOD + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
                        }
                        Debug.logError(transactionError, MODULE);
                        return ServiceUtil.returnError(transactionError);
                    }
                } 
            }
             
             
            
            
            totalFilesProcessed++;
            
        } //end loop on QOH input files
        
        
        if(useCustomLogger) logger.logInfo("******** END (" + METHOD + ") - Time: " + MpStyleUtil.getNowDateTimeString() + " ********\n");
        
        returnMap = ServiceUtil.returnSuccess("Total file procesed: " + totalFilesProcessed);
        
        return returnMap;
    }
    
}//end class
