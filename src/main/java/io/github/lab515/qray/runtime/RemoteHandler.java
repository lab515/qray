package io.github.lab515.qray.runtime;

public interface RemoteHandler {
    String[] execute(String code, String data, Remotee remotee) throws Exception;
	//boolean isRemotable(Object testCase, Method testMethod);
	boolean isTransferable(String className);
	Exception onError(String className, String errData); 
	//Remotype onInvoke(Object testCase, Method method, boolean scoped); // let the local do thejudge
	// for internal log
	void log(String info);
}
