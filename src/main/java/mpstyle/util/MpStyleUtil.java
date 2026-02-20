/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mpstyle.util;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;


/**
 *
 * @author equake58
 */
public class MpStyleUtil {
    
    public static final String module = MpStyleUtil.class.getName();
    
    public final static String CSV_EXT = ".csv";
    public final static String TXT_EXT = ".txt";

    
    /** Return a string composed this way: YYYY_MM_DD_h:m:s.
      @return a composed date time string for now
    */
    public static String getNowDateTimeString() {

        Calendar calendar = Calendar.getInstance();

        //set the current time for Calendar
        calendar.setTimeInMillis(System.currentTimeMillis());

        String year = Integer.toString( calendar.get(Calendar.YEAR) );
        //Calendar.MONTH returns a number between 0 (Jan) to 11 (Dec)
        String month = Integer.toString( calendar.get(Calendar.MONTH) + 1 );
        String day = Integer.toString( calendar.get(Calendar.DAY_OF_MONTH) );

        String hour = Integer.toString( calendar.get(Calendar.HOUR_OF_DAY) );
        String minute = Integer.toString( calendar.get(Calendar.MINUTE) );
        String sec = Integer.toString( calendar.get(Calendar.SECOND) );

      
        String nowTimestampDateString = year + "_" + month + "_" + day + "_" + hour + minute + sec;

        return nowTimestampDateString;

    }
    
     /** *  Method that build a file name for the log file.The file name has this form:
      MSP_YYYYMMDD.log
     * @param logFilePrefix
     * @param logBaseFileName
     * @param logFileExt
      @return the log file name string
    */
    public static String getFileName(String logFilePrefix, String logBaseFileName, String logFileExt) {
      
        Calendar calendar = Calendar.getInstance();

        //set the current time for Calendar
        calendar.setTimeInMillis(System.currentTimeMillis());

        String year = Integer.toString( calendar.get(Calendar.YEAR) );
        //Calendar.MONTH returns a number between 0 (Jan) to 11 (Dec)
        String month = Integer.toString( calendar.get(Calendar.MONTH) + 1 );
        String day = Integer.toString( calendar.get(Calendar.DAY_OF_MONTH) );

        String filename = logFilePrefix + logBaseFileName + "_" + year + month + day + logFileExt;
        Debug.logWarning("########## filename: "+filename, module);

        return filename;

    }
    
    /**
     * 
     * @param fileName
     * @return 
     */
    public static String getFileNameWithTimestamp(String fileName) {
        
        Calendar calendar = Calendar.getInstance();

        //set the current time for Calendar
        calendar.setTimeInMillis(System.currentTimeMillis());

        String year = Integer.toString( calendar.get(Calendar.YEAR) );
        //Calendar.MONTH returns a number between 0 (Jan) to 11 (Dec)
        String month = Integer.toString( calendar.get(Calendar.MONTH) + 1 );
        String day = Integer.toString( calendar.get(Calendar.DAY_OF_MONTH) );

        String hour = Integer.toString( calendar.get(Calendar.HOUR_OF_DAY) );
        String minute = Integer.toString( calendar.get(Calendar.MINUTE) );
        String sec = Integer.toString( calendar.get(Calendar.SECOND) );

        String nowTimestampDateString = year + month + day + "_" + hour + minute + sec;
        
        fileName = fileName + nowTimestampDateString;
        
        return fileName;
    }
    
    /**
     * 
     * @param orderDate
     * @return 
     */
    public static String getMovimodaOrderDateString(Timestamp orderDate) {
        
        StringBuilder mmOrderDateString = null;
        
        if(orderDate == null) {
            Debug.logError("orderDate is null. Cannot create Movimoda order date string.", module);
            return null;
        }
        
        mmOrderDateString = new StringBuilder();
        
        Calendar calendar = Calendar.getInstance();
        
        calendar.setTimeInMillis(orderDate.getTime());
        
        
        String year = Integer.toString( calendar.get(Calendar.YEAR) );
        
        //Calendar.MONTH returns a number between 0 (Jan) to 11 (Dec)
        int month_int = calendar.get(Calendar.MONTH) + 1;
        String month = null;
        
        if(month_int < 10) {
            
            month  = "0" + Integer.toString( month_int );
            
        }else {
            
            month = Integer.toString( month_int );
            
        }
        
        int day_int = calendar.get(Calendar.DAY_OF_MONTH);
        String day = null;
        
        if(day_int < 10) {
            
            day = "0" + Integer.toString(day_int);
            
        }else{
            
            day = Integer.toString( day_int );
            
        }
   
        int hour_int = calendar.get(Calendar.HOUR_OF_DAY);
        String hour = null;
        
        if(hour_int < 10) {
            
            hour = "0" + Integer.toString( hour_int );
            
        }else {
            
            hour = Integer.toString( hour_int );
            
        }
        
        int minute_int = calendar.get(Calendar.MINUTE);
        String minute = null;
        
        if(minute_int < 10) {
            
            minute = "0" + Integer.toString( minute_int );
            
        }else {
            
            minute = Integer.toString( minute_int );
            
        }
        
        int sec_int = calendar.get(Calendar.SECOND);
        String sec = null;
        
        if(sec_int < 10) {
            
            sec = "0" + Integer.toString( sec_int );
            
        }else{
            
            sec = Integer.toString(sec_int);
            
        }
        
        mmOrderDateString.append(day).append("/").append(month).append("/").append(year);
        mmOrderDateString.append(" ").append(hour).append(":").append(minute).append(":").append(sec);
        
        return mmOrderDateString.toString();
    }
    
    /**
     * 
     * @param delegator
     * @param orderStatus
     * @param reportFromDate
     * @param reportThruDate
     * @return 
     */
    public static List<GenericValue> getOrderDateFilterList(Delegator delegator, String orderStatus, Timestamp reportFromDate, Timestamp reportThruDate) {
        
        List<GenericValue> orderList = null;
        
        EntityCondition orderCondition = null;
         
        EntityCondition orderStatusCondition = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, orderStatus);
         
        EntityCondition fromDateCondition = null;
        EntityCondition thruDateCondition = null;
        EntityCondition filterDateCondition = null;
         
         if(UtilValidate.isNotEmpty(reportFromDate)) {
             fromDateCondition = EntityCondition.makeCondition("entryDate", EntityOperator.GREATER_THAN_EQUAL_TO, reportFromDate);
         }
         
         if(UtilValidate.isNotEmpty(reportThruDate)) {
             thruDateCondition = EntityCondition.makeCondition("entryDate", EntityOperator.LESS_THAN_EQUAL_TO, reportThruDate);
         }
          
        if(fromDateCondition != null && thruDateCondition != null) {             
            filterDateCondition = EntityCondition.makeCondition(EntityOperator.AND, fromDateCondition, thruDateCondition);
        }else if(fromDateCondition != null) {
            filterDateCondition = fromDateCondition;
        }else if(thruDateCondition != null) {
            filterDateCondition = thruDateCondition;
        }
        
        if(filterDateCondition != null) {
            orderCondition = EntityCondition.makeCondition(EntityOperator.AND, orderStatusCondition, filterDateCondition);
        }else{
            orderCondition = orderStatusCondition;
        }
        
        //Get order list order by entryDate (asc)
        try {
         
            orderList = delegator.findList("OrderHeader", orderCondition, null, UtilMisc.toList("entryDate"), null, false);
            
        }catch(GenericEntityException gee) {
            Debug.logError(gee, module);
            return null;
        }
        
        
        
        return orderList;
        
        
        
    }
    
    /**
     * 
     * @param inputString
     * @param charToRemove
     * @return 
     */
    public static String removeCharFromString(String inputString, String charToRemove) {
        
        if(inputString.contains(charToRemove)) {
            
            inputString = inputString.replace(charToRemove, "");
            
        }
        
        return inputString;
        
    };
    
    
} //end class
