package io.github.lab515.qray.runtime;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

// as base, we didn't specify the remotetype, only provide a annoation tag
public abstract class Remoto implements Remotee {
    public static final int              C_RESULTS = 3;
    // solve the ctor issue, and make sure getVar can be used as early as we can
    private static ThreadLocal<Object[]>                _q_cxt =  new ThreadLocal<Object[]>(); // me,. bind, varMethod, that
    // chekc if it's in remote mode, the lifecycle started at Remotee.execute method
    public static boolean isRemoteMode(){ return _q_cxt.get() != null;}
    // for any thread, getRemoto will give the current remoting object
    public static Remotee getRemotee(){
        Object[] cxt = _q_cxt.get();
        return cxt != null ? (Remotee)cxt[0] : null;
    }
    public static Remoto getRemoto() {
        Remotee remotee = getRemotee();
        return remotee != null ? remotee.getStub() : null;
    }
    // get properties as object
    public static <T> T getObject(String vName) throws Exception {
        Remotee rm = getRemoto();
        return rm != null ? (T)rm.getStub().getBindObject(vName) : null;
    }
    // get property as string
    public static String getVar(String vName) throws Exception {
        Remotee rm = getRemoto();
        return rm != null ? rm.getStub().getBindVar(vName) : null;
    }

    // main entry for qray
    public static String execute(Object that, Object bind, String data) {
        Remotee target = null;
        ArrayList<Remotee> remotees = null;
        try {
            // me,. bind, var, that, as step 1.
            _q_cxt.set(
                    new Object[]{
                            null,
                            bind,
                            bind.getClass().getMethod("getVariable", String.class),
                            that}); // no getDeclaredMethod
            String[] segs = unpackData(data); // pd 0: methods, 1: files, 2:
            // object fields info!!!
            String[] methods = segs[0].split("\n"); // awesome
            LinkedHashMap<String, Method> map = new LinkedHashMap<String, Method>();
            Class clz = null;
            try{
                clz = Thread.currentThread().getContextClassLoader().loadClass(methods[0].trim());
            }catch(ClassNotFoundException e){
                clz = that.getClass().getClassLoader().loadClass(methods[0].trim()); // this is the class
            }
            if (!Remotee.class.isAssignableFrom(clz)) return "Error: remote object must always implement Remoto interface!";
            remotees = new ArrayList<Remotee>();
            target = unpackBase(segs.length > 2 ? segs[2] : null,clz, null, null, remotees);
            return target.getStub().executeInternal(that, bind, segs, methods, remotees);
        } catch (Exception e) {
            e.printStackTrace();
            return "Exception during initiate remote object from remote: " + e.getClass().getCanonicalName() + "/" +  e.getMessage();
        }finally{
            if(target != null)target.getStub().uninitContext();
            if(remotees != null) remotees.clear();
        }
    }

    // NOTE: all Remotee's fields are not remotable!!!!!, starts with _q_!!
    private String[]                     _q_data             = null; // remote
    private String[]                     _q_addFiles         = null; // local, thread safe (owned by user)
    private String[]                     _q_addClses         = null; // local, owned by user
    private ArrayList<Field>             _q_fields           = null; // local. only in packeMe unPackMe (synchronized)
    //private ArrayList<Remotee>            _q_runners          = null; // remote is safe, local need sync

    private String                       _q_provider         = null; // should be safe, inited in early stage
    private String                       _q_nonProviders     = null; // same

    private Object                       _q_binds            = null;
    private Method                       _q_getVar           = null;
    private Object                       _q_that             = null;
    private ArrayList<Thread>            _q_threads          = null; // this will mark all threads who owns this Remotee
    private Remotee                      _q_target           = null;
    private boolean                      _q_rollback         = false; // added: but never used, it's more application related
    //public abstract void onTestDone(Method m); // callback before each test case run
    @Override
    public Remoto getStub(){return this;}

    //public abstract String onError(Throwable e) throws Exception;
    public abstract Object onResult(Method m, Object ret, Exception e) throws Exception;
    public abstract void onTestRun(Method m) throws Exception;
    public abstract void onRemoting(Method[] ms) throws Exception;
    public abstract void onRemoted(Method[] ms) throws Exception;

    public abstract void onReturning(Method[] ms) throws Exception;
    public abstract void onArrived(Method[] ms) throws Exception;
    public abstract Object getDryReturn(Method method); // this is for the dryrun return results

    public abstract String getConfig(String name);
    // object de-serialization/serialization
    public abstract <T> T unpack(String packedData, Class optionalClazz) throws Exception;
    public abstract String pack(Object t)throws Exception;
    // dryrun return object if necessary
    public abstract String getOutput() throws Exception;
    public abstract String zip(String data) throws Exception;
    public abstract String zip2(byte[] data) throws Exception;
    public abstract String unzip(String data) throws Exception;
    public abstract void output(String info);

    public String getProvider(){
        initContext();
        return _q_provider;
    }

    public boolean isRollback(){
        initContext();
        return _q_rollback;
    }

    public synchronized void initContext(){
        Object[] cxt = _q_cxt.get();
        if((_q_getVar != null) == (cxt != null))return;
        Remotee rmt = null;
        if(_q_getVar == null) {
            _q_binds = cxt[1];
            _q_getVar = (Method) cxt[2];
            if(cxt[0] == null)cxt[0] = _q_target;
            else rmt = (Remotee)cxt[0];
            String p = null;
            try{
                p = (String)_q_getVar.invoke(_q_binds,"_provider");
            }catch(Exception e){e.printStackTrace();} // normally it won't be a issue
            if (p != null) p = p.trim();
            if (p == null || p.length() < 1) p = null; // hard set
            _q_provider = p;
            try {
                p = (String) _q_getVar.invoke(_q_binds, "_nonProviders"); // hard set
            }catch (Exception e){e.printStackTrace();}
            if (p != null && (p = p.trim()).length() < 1) p = null;
            else p = "," + p + ",";
            _q_nonProviders = p;
            try{
                p = (String)_q_getVar.invoke(_q_binds,"_rollback");
            }catch(Exception e){e.printStackTrace();} // normally it won't be a issue
            if (p != null && p.trim().equalsIgnoreCase("true"))_q_rollback = true;
            _q_that = cxt[3];
        }else{
            _q_cxt.set(
                    new Object[]{
                            _q_target,
                            _q_binds,
                            _q_getVar,
                            _q_that}); // no getDeclaredMethod
        }
        if(rmt == null)rmt = _q_target;
        if(rmt.getStub()._q_threads == null)rmt.getStub()._q_threads = new ArrayList<Thread>();
        if(rmt.getStub()._q_threads.indexOf(Thread.currentThread()) < 0)rmt.getStub()._q_threads.add(Thread.currentThread());
    }
    private void uninitContext(){
        // we need to get the current threads, for each threads
        Object[] cxt = _q_cxt.get();
        if(cxt != null) {
            for(int i  =0; i < cxt.length;i++)cxt[0] = null;
        }
        _q_cxt.remove(); // remove the refer
        // loop through all the threads
        if(_q_threads == null || _q_threads.size() < 1)return;
        try {
            Field f = Thread.class.getDeclaredField("threadLocals");
            f.setAccessible(true);
            Method m = null;
            for (Thread t : _q_threads) {
                if (t == null) continue;
                Object map = f.get(t);
                if (map == null) continue;
                if (m == null){
                    m = map.getClass().getDeclaredMethod("remove", ThreadLocal.class);
                    m.setAccessible(true);
                }
                cxt = (Object[]) m.invoke(map, _q_cxt);
                if (cxt != null) {
                    for (int i = 0; i < cxt.length; i++) cxt[0] = null;
                }
                cxt = null;
            }
        }catch  (Exception e){
            e.printStackTrace();
        }
        _q_threads.clear();
        _q_threads = null;
    }

    boolean canPack(Class cz, Remotable fieldAnno, boolean paraMode){
        if(fieldAnno != null)return fieldAnno.type() == Remotype.UNDEFINED || fieldAnno.type() == Remotype.REMOTE; // SKIP, LOCAL
        if(cz.isArray()){
            return paraMode ? canPack(cz.getComponentType(),null,true) : false;
        }
        if(cz.isPrimitive() || Remotee.class.isAssignableFrom(cz))return true;
        String c = cz.getCanonicalName();
        if(c.startsWith("java.lang.") && ".Integer.Double.Long.Float.String.Boolean.Short.Character.Byte.".indexOf(c.substring(9) + ".") >= 0)return true;
        fieldAnno = (Remotable)cz.getAnnotation(Remotable.class);
        if(fieldAnno == null)return false;
        return fieldAnno.type() == Remotype.REMOTE || fieldAnno.type() == Remotype.UNDEFINED;
    }
    int hasPacked(Remotee that, ArrayList<Remotee> remotees) throws Exception{
        int ret = remotees.indexOf(that);
        if(remotees.size() < 1 && that != _q_target) throw new Exception("packing object first one must be Remotee object itself!");
        if(ret < 0) remotees.add(that);
        return ret;
    }
    synchronized String packMe(boolean staticOnly, boolean pack, ArrayList<Remotee> remotees) throws Exception{
        int has = hasPacked(_q_target, remotees);
        if(!pack)return null;
        if(has >= 0)return "#" + has; // a ref no
        if(_q_fields == null) {
            _q_fields = new ArrayList<Field>();
            Class cc = _q_target.getClass();
            HashSet<String> checker = new HashSet<String>();
            while (cc != null && cc != Remotee.class && cc != Remoto.class) {
                for (Field fld : cc.getDeclaredFields()) {
                    if (Modifier.isFinal(fld.getModifiers()))
                        continue; // final is not easy to set, and we don't support
                    // no package
                    if (!canPack(fld.getType(), fld.getAnnotation(Remotable.class), false)) continue;
                    if (checker.contains(fld.getName())) continue; // child > parent
                    _q_fields.add(fld);
                }
                cc = cc.getSuperclass();
            }
        }
        if(_q_fields.size() < 1)return null;
        ArrayList<String> ret = new ArrayList<String>();
        //String[] ret = new String[_q_fields.size() * 2];
        for(int i  =0; i < _q_fields.size();i++){
            Field f = _q_fields.get(i);
            if(!Modifier.isStatic(f.getModifiers()) && staticOnly)continue;
            ret.add(f.getDeclaringClass().getName() + "." + f.getName());
            // find the object
            f.setAccessible(true);
            Object t = f.get(Modifier.isStatic(f.getModifiers()) ? null : _q_target);
            if(t == null)ret.add(null);
            else if(t instanceof Remotee)ret.add(((Remotee)t).getStub().packMe(false, true, remotees));
            else ret.add(pack(t));
        }
        if(ret.size() < 1)return null;
        String[] rr = new String[ret.size()];
        ret.toArray(rr);
        ret.clear();
        return "#" + _q_target.getClass().getName() + "#" + packData(rr);
    }
    // remote only, thread safe
    static Remotee unpackBase(String packedMe, Class clz, Remoto packer, Remotee target, ArrayList<Remotee> remotees) throws Exception{
        String rc = null;
        if(packedMe != null && packedMe.length() > 0){
            if(!packedMe.startsWith("#"))throw new Exception("invalid RunBase package, should starts with #clazz#data, or #index");
            int p = packedMe.indexOf('#',1);
            if(p < 0){
                return remotees.get(Integer.parseInt(packedMe.substring(1)));
            }
            rc = packedMe.substring(1,p);
            packedMe = packedMe.substring(p+1);
        }
        if(target == null){
            if(rc != null && !clz.getName().equals(rc)){
                clz = clz.getClassLoader().loadClass(rc);
            }

            if(Remotee.class.isAssignableFrom(clz)){
                Constructor ctor = clz.getConstructor();
                ctor.setAccessible(true);
                target = (Remotee) ctor.newInstance();
                target.getStub()._q_target = target;
            }
            if(target == null)throw new Exception("failed initiate Remotee Object: " + clz.getCanonicalName());
        }
        if(packer == null)packer = target.getStub();
        packer.hasPacked(target, remotees);
        // first of all, let's pack it
        target.getStub().unpackMe(packedMe, remotees);
        return target;
    }

    synchronized void unpackMe(String packedData,ArrayList<Remotee> remotees) throws Exception{
        if(_q_fields == null) _q_fields = new ArrayList<Field>();
        if (packedData == null || packedData.length() < 1)return;
        String[] arr = unpackData(packedData);
        if(arr.length > 1 && _q_fields.size() == 0){
            // initiate the stuff
            Class m = _q_target.getClass();
            for(int i = 0; i <arr.length-1;i+=2){
                String clsName = arr[i];
                int p = clsName.lastIndexOf('.');
                String fname = clsName.substring(p+1).trim();
                clsName = clsName.substring(0,p).trim();
                if(fname.length() < 1 || clsName.length() < 1)break;
                while(m != null && m != Remotee.class && !m.getName().equals(clsName))m = m.getSuperclass();
                if(m == null || m == Remotee.class)break;
                _q_fields.add(m.getDeclaredField(fname));
            }
        }
        if(arr == null || arr.length < 2 || arr.length != _q_fields.size() * 2){
            throw new Exception("remote packed object attributes is not matching local fields, local: " + _q_fields.size());
        }
        int idx = 0;
        for(int i = 0; i < _q_fields.size() && idx < arr.length  -1;i++){
            // no check anymore!!
            Field f = _q_fields.get(i);
            if(!arr[idx].equals(f.getDeclaringClass().getName() + "." + f.getName()))continue;
            f.setAccessible(true);
            Object val = null;
            if(arr[idx+1] != null){
                if(Remotee.class.isAssignableFrom(f.getType()) || (f.getType() == Object.class && arr[idx+1].startsWith("#")))val = unpackBase(arr[i*2+1],f.getType(),this, null, remotees);
                else val = unpack(arr[idx+1],f.getType());
            }
            // FIX: Remotee may not Remoto (stub)
            if(val != null && Remotee.class.isAssignableFrom(f.getType()) && !f.getType().isAssignableFrom(val.getClass())){
                val = ((Remotee)val).getStub();
                if(val != null && f.getType().isAssignableFrom(val.getClass()))f.set(Modifier.isStatic(f.getModifiers()) ? null : _q_target, val);
                else throw new Exception("remote pack error: remotee type not match for field: " + arr[idx] + ", type: " + f.getType().getName() +", value:" + (val != null ? val.getClass().getName() : "null"));
            }else f.set(Modifier.isStatic(f.getModifiers()) ? null : _q_target, val);
            idx+=2;
        }
        if(idx < arr.length)throw new Exception("remote packed unknown fields: " + arr[idx]);
    }

    private String executeInternal(Object thatt, Object bind, String[] segs, String[] methods,ArrayList<Remotee> remotees) {
        initContext(); // just in case, actually it's already called in ctor
        // // pd 0: methods, 1: files, 2: object fields info!!!
        // set the transferred files
        _q_data = unpackData(segs[1]);
        LinkedHashMap<String, Method> map = new LinkedHashMap<String, Method>();
        Class cclz = _q_target.getClass();
        LinkedList<Class> clses = new LinkedList<Class>();
        //Method errorHandler = null;
        while (cclz != null && cclz != Remotee.class && cclz != Remoto.class && cclz != Object.class) {
            // FIX for ADF, this is a hard coded stuff
            // QInvoker regression, add a special logic to check Remotable annoation
            //if(cclz.getName().indexOf(".saf.") < 0) {
            try{
                for (Method m : cclz.getDeclaredMethods()) {
                    map.put(m.toGenericString(), m);
                    //if (m.getName().equals("onError") && Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()) && m.getReturnType() != null && m.getReturnType().equals(String.class)) {
                    //    Class[] ss = m.getParameterTypes();
                    //    if (ss != null && ss.length == 1 && ss[0].equals(Throwable.class)) {
                    //        errorHandler = m;
                    //    }
                    //}
                }
            }catch(Throwable t){}
            cclz = cclz.getSuperclass();
        }


        // set object attrs
        try{
            //unpackMe(segs.length > 2 ? segs[2] : null);
            // put order
            ArrayList<Method> finalList = new ArrayList<Method>();
            ArrayList<String> finalPs = new ArrayList<String>();
            int testMethod = -1;
            for (int i = 1; i < methods.length; i++) {
                String m = methods[i].trim();
                if (m.length() < 1)
                    continue;
                if (m.startsWith("#")) {
                    testMethod = finalList.size();
                    m = m.substring(1);
                }
                // add: final change: add parameters
                int p = m.indexOf('@');
                String ps = null;
                if(p > 0){
                    ps = m.substring(p+1).trim();
                    m = m.substring(0,p).trim(); // the real method
                }
                if (map.containsKey(m)){
                    finalList.add(map.get(m));
                    finalPs.add(ps);
                }else {
                    return "method: " + m + " is not defined in class: " + methods[0];
                }
            }
            if (finalList.size() < 1 || testMethod < 0)
                return "there is no test method defined in method list";
            // ok, we need to execute method based on the order, and catch exception
            // as needed
            boolean stop = false;
            Method[] mtds = new Method[finalList.size()];
            finalList.toArray(mtds);
            finalList.clear();
            String[] result = new String[mtds.length * C_RESULTS + 1]; // exception,
            // log, added:
            // return object
            // the last one
            // will be the
            // object
            // attributes
            Object[] rets = new Object[mtds.length];
            try{
                this.onArrived(mtds);
            }catch(Exception e){
                // we do nothing, but just print
                e.printStackTrace();
            }
            for (int i = 0; i < mtds.length; i++) {
                if (i <= testMethod && stop) {
                    continue;
                }
                Method m = mtds[i];
                try {
                    m.setAccessible(true);
                    // do the parameters
                    String ps = finalPs.get(i);
                    Object [] ops = null;
                    Class[] tps = m.getParameterTypes();
                    if(ps != null && ps.length() > 0){
                        String[] pss = unpackData(unzip(finalPs.get(i)));
                        ops = new Object[pss.length];
                        if(ops.length == tps.length){
                            for(int p  =0; p < ops.length; p++){
                                if(Remotee.class.isAssignableFrom(tps[p]) ||
                                        (tps[p] == Object.class && pss[p] != null && pss[p].startsWith("#")))ops[p] = unpackBase(pss[p],tps[p],this, null, remotees);
                                else if(pss[p] != null)ops[p] = unpack(pss[p], tps[p]);
                            }
                        }
                    }

                    if((tps != null ? tps.length : 0) == (ops != null ? ops.length : 0)){
                        Object ret = m.invoke(Modifier.isStatic(m.getModifiers()) ? null : _q_target,ops);
                        if(m.getReturnType() != Void.class && ret != null){
                            // serailzie it
                            rets[i] = ret;
                            //result[i * C_RESULTS + 2] = pack(ret);
                        }
                    }else
                        result[i * C_RESULTS] = "invalid parameters number to call!";
                } catch (Throwable e) {
                    e.printStackTrace();
                    if (e.getCause() != null)
                        e = e.getCause();
                    ByteArrayOutputStream baos = null;
                    PrintStream ps = null;
                    try {
                        //if (errorHandler != null)
                        result[i * C_RESULTS] = pack(e);
                                    //+ errorHandler.invoke(Modifier.isStatic(errorHandler.getModifiers()) ? null : _q_target, e);
                    } catch (Exception e2) {
                        baos = new ByteArrayOutputStream();
                        ps = new PrintStream(baos);
                        e.printStackTrace(ps);
                        result[i * C_RESULTS] = baos.toString("utf-8");
                        //result[i * C_RESULTS] = "handling exception: " + e.getClass().getSimpleName() + ":" + e.getMessage() + " failed: " + e2.getClass().getSimpleName() + ":" + e2.getMessage();
                    } finally {
                        try {
                            if (ps != null)ps.close();
                        } catch (Exception ee) {}
                        try {
                            if (baos != null)baos.close();
                        } catch (Exception ee) {}
                    }
                    if (result[i * C_RESULTS] == null || result[i * C_RESULTS].length() < 1)
                        result[i * C_RESULTS] = "unknown exception";
                    stop = true;
                }
                // add: also we handle the log info
                String log = getOutput();
                if (log == null)log = "";
                result[i * C_RESULTS + 1] = log;
            }
            try{
                this.onReturning(mtds);
            }catch(Exception e){
                // we do nothing, but just print
                e.printStackTrace();
            }
            //clean up!!
            remotees.clear();
            // step1, pack me
            result[result.length - 1] = packMe(false,true, remotees);
            // pack all rest stuff
            for(int i  =0; i < rets.length;i++){
                if(rets[i] != null && result[i * C_RESULTS] == null){ // no exception
                    result[i * C_RESULTS + 2] = (rets[i] instanceof Remotee) ? ((Remotee)rets[i]).getStub().packMe(false,  true, remotees) : pack(rets[i]);
                }
            }
            return "#" + zip(packData(result));
        }catch(Exception e){
            e.printStackTrace();
            return "unexpected runtime error: " + e.getMessage() + (e.getCause() != null ? e.getCause().getMessage() : "");
        }

    }

    /**
     * Simple format of data packing
     *
     * @param arr
     *            array of string
     * @return string
     */
    static String packData(String[] arr) {
        return packData(arr, -1);
    }

    /**
     * Simple format of data packing
     *
     * @param arr
     *            array of string
     * @param max
     *            the max items to pack
     * @return string
     */
    static String packData(String[] arr, int max) {
        if (arr == null || arr.length < 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length && (max < 1 || i < max); i++) {
            if (arr[i] == null) {
                sb.append("-1:");
            } else {
                sb.append(arr[i].length());
                sb.append(":");
                sb.append(arr[i]);
            }
        }
        String s = sb.toString();
        sb.setLength(0);
        return s;
    }

    static String[] unpackData(String value) {
        if (value == null || value.length() < 1) {
            return null;
        }

        int len = value.length();
        if (len < 2) {
            return null;
        }
        int pos = 0;
        List<String> ret = new ArrayList<String>();
        int segLen = 0;
        int startPos = 0;
        while (pos < len - 1) {
            // try to get the next index
            segLen = value.indexOf(":", pos);
            if (segLen <= pos) {
                break;
            }
            startPos = segLen + 1;
            segLen = Integer.parseInt(value.substring(pos, segLen));
            if (segLen >= 0 && startPos + segLen <= len) {
                ret.add(value.substring(startPos, startPos + segLen));
                pos = startPos + segLen;
            } else if (segLen == -1) {
                ret.add(null);
                pos = startPos;
            } else {
                break;
            }
        }

        if (pos != len || ret.size() < 1) {
            return null;
        }
        String[] rets = new String[ret.size()];
        ret.toArray(rets);
        ret.clear();
        return rets;
    }



    protected <T> T getBindObject(String vName) throws Exception {
        if(vName == null || vName.length() < 1)return (T)null;
        initContext();
        if (_q_getVar == null)
            throw new Exception("bind properties not initiated, contact amdin!");
        T ret = null;
        if(_q_provider != null &&
                !(vName.equals("_provider") || vName.equals("_nonProviders"))){
            if(_q_nonProviders == null || _q_nonProviders.indexOf( "," + vName + ",") < 0){
                ret = (T)_q_getVar.invoke(_q_binds, _q_provider + "." + vName);
                if(ret != null)return ret;
            }
        }
        return (T) _q_getVar.invoke(_q_binds, vName);
    }

    protected String getBindVar(String vName) throws Exception {
        // there are certain keys
        Object t = getBindObject(vName);
        if (t instanceof String)
            return (String) t;
        else
            return t != null ? t.toString() : null;
    }

    public String readFileEncoded(String name) throws Exception {
        if (name == null || name.length() < 1)
            return null;
        if (_q_data == null)
            throw new Exception("no files transfered to server, please use anno Remotable(resources=)!");
        String hit = null;
        String hitName = null;
        for (int i = 0; i < _q_data.length - 1; i += 2) {
            if(_q_data[i] == null || _q_data[i].length() < 1)continue;
            if (hit == null && _q_data[i].equals(name)) {
                hit = _q_data[i + 1];
                hitName = _q_data[i];
                break;
            } else if (hit == null && _q_data[i].indexOf(name) >= 0) {
                hit = _q_data[i + 1];
                hitName = _q_data[i];
            }
        }
        if(hitName == null)throw new Exception("file " + name + " is not present, please include it in Remotable resources");
        else if(hit == null){
            throw new Exception(
                    (hitName.equals(name) ?
                            "file " + name : "file " + name +"'s matcher " + hitName) + " is not transfered, please check your local data folder for existence!");
        }
        // return hit.length() > 0 ?
        // ExUtils.inflateDataBlock(QRUtils.decodeBase64(hit)) : new byte[0];
        return hit;
    }

    public void setExtraDataRetention(String[] fileNames){
        _q_addFiles = fileNames;
    }

    public void setExtraDependentClasses(String[] clses){
        _q_addClses = clses;
    }

    public String[] getExtraDataRetention(){
        return _q_addFiles;
    }

    public String[] getExtraDependentClasses(){
        return _q_addClses;
    }
}