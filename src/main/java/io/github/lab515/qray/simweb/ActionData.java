package io.github.lab515.qray.simweb;

import java.util.ArrayList;
import java.util.List;

/**
 * ActionData for simweb action call
 * 
 * @author mpeng
 * 
 */
public class ActionData {
    public String[] headers = null;
    public String[] strs = null;
    public String err = null;
    public String str = null;

    /**
     * constructor
     * @param strVal string value
     */
    public ActionData(String strVal){
        str = strVal;
    }
    
    /**
     * constructor
     * @param hdrs http headers
     * @param vals http values
     * @param errInf error information
     * @param strVal string values
     */
    public ActionData(String[] hdrs, String[] vals, String errInf, String strVal) {
        headers = hdrs;
        strs = vals;
        err = errInf;
        str = strVal;
    }

    /**
     * unpack the action String to string Array
     * @param Value action String
     * @return action String array
     */
    public static String[] _unpack(String Value) {
        if (Value == null || Value.length() < 1)
            return null;
        int len = Value.length();
        if (len < 2)
            return null;
        int pos = 0;
        List<String> ret = new ArrayList<String>();
        int segLen = 0;
        int startPos = 0;
        while (pos < len - 1) {
            // try to get the next index
            segLen = Value.indexOf(":", pos);
            if (segLen <= pos)
                break;
            startPos = segLen + 1;
            segLen = Integer.parseInt(Value.substring(pos, segLen));
            if (segLen >= 0 && startPos + segLen <= len) {
                ret.add(Value.substring(startPos, startPos + segLen));
                pos = startPos + segLen;
            } else if (segLen == -1) {
                ret.add(null);
                pos = startPos;
            } else
                break;
        }

        if (pos != len || ret.size() < 1)
            return null;
        String[] rets = new String[ret.size()];
        ret.toArray(rets);
        ret.clear();
        return rets;
    }

    /**
     * pack String array to String
     * @param arr action String array
     * @return action String
     */
    public static String _pack(String[] arr) {
        if (arr == null || arr.length < 1)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == null)
                sb.append("-1:");
            else {
                sb.append(arr[i].length());
                sb.append(":");
                sb.append(arr[i]);
            }
        }
        String s = sb.toString();
        sb.setLength(0);
        return s;
    }

    /**
     * pack actions to String
     * @return action String
     */
    public String pack() {
        String[] sets = new String[4];
        sets[0] = str;
        sets[1] = _pack(strs);
        sets[2] = _pack(headers);
        sets[3] = err;
        return "#" + _pack(sets);
    }

    /**
     * unpack String to ActionData
     * @param val input String value
     * @return ActionData object
     */
    public static ActionData unpack(String val) {
        if (val == null || !val.startsWith("#"))
            return null;
        String[] sets = _unpack(val.substring(1));
        if (sets == null || sets.length != 4)
            return null;
        return new ActionData(_unpack(sets[2]), _unpack(sets[1]), sets[3],
                sets[0]);
    }
    public static void main(String [] args){
        ActionData ad  = new ActionData(null);
        ad.headers = new String[]{"method","sel","where","sort","item"};
        ad.strs = new String[]{"GetDefectStatusMatrixByTeam","Name,Number,Status,Custom_SLA2,Scope.Name,Timebox.Name,IsClosed,Team.Name","((Scope.Name='b1402 HCM Integration'|Scope.Name='b1402 PLT Foundation'|Scope.Name='b1402 PLT MDF'|Scope.Name='b1402 PLT JDM'|Scope.Name='b1402 PLT Admin Tools'|Scope.Name='b1402 PLT Audit'|Scope.Name='b1402 PLT Cache Service'|Scope.Name='b1402 PLT Foundation'|Scope.Name='b1402 PLT Home Page'|Scope.Name='b1402 PLT Job Scheduler'|Scope.Name='b1402 PLT Outlook Integration'|Scope.Name='b1402 PLT RBP'|Scope.Name='b1402 PLT Rule Engine'|Scope.Name='b1402 PLT Search'|Scope.Name='b1402 PLT SEB'|Scope.Name='b1402 PLT Security and Performance'|Scope.Name='b1402 PLT SSO'|Scope.Name='b1402 PLT Homepage'|Scope.Name='b1402 Mobile (MOB)'|Scope.Name='b1402 Web Service API (API)'|Scope.Name='b1402 Platform (PLT)');(Team.Name='PLT Integration'|Team.Name='PLT MDF Shanghai'|Team.Name='PLT Metadata Framework'|Team.Name='PLT JDM'|Team.Name='PLT Admin Tools'|Team.Name='PLT Audit'|Team.Name='PLT Cache Service'|Team.Name='PLT Foundation'|Team.Name='PLT Home Page'|Team.Name='PLT Job Scheduler'|Team.Name='PLT Outlook Integration'|Team.Name='PLT RBP'|Team.Name='PLT Rule Engine'|Team.Name='PLT Search'|Team.Name='PLT SEB'|Team.Name='PLT Security'|Team.Name='PLT SSO'|Team.Name='PLT Home Page'|Team.Name='PLT Foundation'|Team.Name='PLT Mobile Project Team'|Team.Name='PLT API'|Team.Name='PLT UI Commons'|Team.Name='(None)'));AssetState!='Dead'","Scope.Parent.Name","TeamDefectStatusMatrix"};
        String aa = ad.pack();
        System.out.print(aa);
        
    }
}