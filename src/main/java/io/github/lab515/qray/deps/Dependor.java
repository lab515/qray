package io.github.lab515.qray.deps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.lab515.qray.conf.Config;
import io.github.lab515.qray.utils.ExUtils;

public class Dependor {
	public static String[] getDependencies(Class target, String[] extraClasses, String clsPattern) throws Exception{
		Pattern pt = Pattern.compile(clsPattern);
		String[] ret =  handleDependencies(target,extraClasses, pt);
		if(ret == null)throw new Exception("dependency check failed! null returned!");
		// fix: we need to chekc if last calss is starts With *
        //String last = ret[ret.length - 1]; 
        //if(last.startsWith("*") && 
        //        (last.length() != targetCls.length() + 1 || !last.endsWith(targetCls))){
        //    int found = -1;
        //    String cmp = "*" + targetCls;
        //    for(int i = 0; i < ret.length;i++){
        //        String t = ret[ret.length - i - 1]; 
        //        if(!t.startsWith("*") || (t.equals(cmp) && (found = ret.length - i - 1) >= 0))break;
        //    }
        //    if(found < 0)throw new Exception("dame, target class is missing!");
        //    if(found != ret.length - 1){
        //        ret[found] = ret[ret.length - 1];
        //        ret[ret.length -1] = cmp;
        //    }
        //}
        for(int i = 0; i < ret.length;i++){
            if(Config.debugMode)Config.getRemoteHandler().log(ret[i]);
            if(ret[i].startsWith("*"))ret[i] = ret[i].substring(1);
        }
        String last = ret[ret.length - 1];
		if(!last.equals(target.getName().replace('.', '/'))){
			throw new Exception("target class should be the last one!!");
		}
		return ret;
	}
	
	private static String[] handleDependencies(Class cls,String[] extraClasses, Pattern pt) throws Exception{
		HashSet<String> target = new HashSet<String>();
		String prefix = cls.getName();
		if(prefix.lastIndexOf('.') >= 0)prefix = prefix.substring(0,prefix.lastIndexOf('.') + 1);
		else prefix = null;
		String targetCls = cls.getName().replace('.', '/'); 
		target.add(targetCls);
		if(extraClasses != null && extraClasses.length > 0){
		    for(String c : extraClasses){
		        if(c == null || c.length() < 1)continue;
				if(!Config.isRemoteBaseClass(c) && (prefix == null || !c.startsWith(prefix)) && pt != null) {
					Matcher m = pt.matcher(c); //ADD the pattern checking logic since here
					if (m == null || !m.matches()) continue;
				}
		        target.add(c.replace('.', '/'));
		    }
		}
		LinkedHashMap<String,HashSet<String>> mapp = new LinkedHashMap<String,HashSet<String>>();
		LinkedHashMap<String,String> inherMap = new LinkedHashMap<String,String>();
    	while(true){
    		String newCls = null;
    		for(String s : target){
    			if(!mapp.containsKey(s)){
    				newCls = s;
    				mapp.put(s, null);
    				break;
    			}
        	}
    		if(newCls == null)break;
    		HashSet<String> temp = new HashSet<String>();
    		String[] ans = CLVisitor.process(ExUtils.readClassBytes(cls, newCls), temp, pt,prefix);
    		if(temp.size() > 0){
    			target.addAll(temp);
    			mapp.put(newCls, temp);
    		}
    		if(ans != null){
    		    for(String an : ans){
    		        inherMap.put(newCls + "-" + an,"");
    		    }
    		}
    	}
    	String [] rr = new String[target.size()];
    	target.remove(targetCls);
    	target.toArray(rr);
    	rr[rr.length-1] = targetCls;
    	return calculateDependencies(targetCls,rr,mapp, inherMap);
    }
	
	private static class Sorter implements java.util.Comparator<String>{
	    private LinkedHashMap<String,String> ins;
	    public Sorter(LinkedHashMap<String,String> in){
	        ins = in;
	    }
        @Override
        public int compare(String o1, String o2) {
            if(o1 == null)return 1;
            else if(o2 == null)return -1;
            else if(ins.containsKey(o1 + "-" + o2))return 1;
            else if(ins.containsKey(o2 + "-" + o1))return -1;
            else return 0;
        }
	    
	}
	
    private static String[] calculateDependencies(String targetCls, String[] target, LinkedHashMap<String,HashSet<String>> mapp, LinkedHashMap<String,String> inherMap) throws Exception{
    	// ok, for each class, we must eliminate the rest of it
    	int left = target.length;
    	String[] ret = new String[left];
    	// figure out the sequence
    	// add: we just need to load testobjects related to the last
    	// change: all classes are ordered and grouped (so some classes must be in one class loader
    	String[] chks = new String[left];
    	int chkCnt = 0;
    	int cnt = 0;
    	
    	while(left > 0){
    		cnt = 0;
    		for(int i = 0; i < target.length;i++){
    			String ss = target[i];
    			if(ss == null || (i == target.length - 1 & cnt > 0))continue;
    			HashSet<String> p = mapp.get(ss);
    			if(p != null){
    				if(p.size() > 0){
	    				// first of all, remove things that still goes
	    				chkCnt = 0;
	    				for(String k : p){
	    					if(mapp.get(k) == null){
	    						chks[chkCnt++] = k;
	    					}
	    				}
	    				if(chkCnt == p.size()){
	    					p.clear();
	    					cnt++; // just for a flag
	    				}else{
	    					for(int j = 0; j < chkCnt;j++)p.remove(chks[j]);
	    				}
	    				continue;
    				}
    			}
    			if(p == null || p.size() < 1){
    				ret[ret.length - left] = ss;
    				mapp.put(ss, null);
    				left--;
    				cnt++;
    				target[i] = null;
    				if(left < 1)break;
    			}
    		}
    		if(cnt < 1)break;
    	}
    	// circle reference, we must put them in one giant loader once for all
    	if(left > 0){
    	    // FIX: check if circle reference depended on targetCls
    	    int targetInvolved = 0; // <=0 no , 1, depend, 2 inclided
    	    for(int i = 0; i < ret.length - left;i++){ // remove all
    			mapp.remove(ret[i]);
    		}
    		if(mapp.containsKey(targetCls))targetInvolved = 2;
    		LinkedHashMap<String,Integer> ops = new LinkedHashMap<String,Integer>();
    		int leftCnt = 0;
    		for(int i = 0; i < target.length;i++){
    			if(target[i] != null){
    			    ops.put(target[i], leftCnt);
    				target[leftCnt++] = target[i];
    			}
    		}
    		// leftCnt == left
    		// repopulate all with a simple way, no peroformance concern
            // define a matxit for the loop calculation
            byte[] matrix = new byte[left * left]; // this might not work, and give oom error, if size is larger than 10000
            // recommend the size should be less than 1000, 1mb memory
            
    		//HashSet<String> nn = new HashSet<String>();
    		Stack<Integer> stack = new Stack<Integer>();
    		// awesome, now, we check all stuff
    		for(int i =0; i < left;i++){
    			HashSet<String> deps = mapp.get(target[i]);
    			// remove the things already solved
    			if(deps.contains(targetCls)){
    			    if(targetInvolved == 0)targetInvolved = 1;
    			    if(targetInvolved != 2)deps.remove(targetCls); 
    			}
    			for(String dep : deps){
    			    //nn.add(target[i] + "-" + dep);
    			    Integer f = ops.get(dep);
                    matrix[i*left + f] = 1; // dep and visited
                    stack.add((i << 16) | f);
    			}
    		}
    		
    		int[] revs = new int[left+1];
            ArrayList<Integer> revData = new ArrayList<Integer>();
            for(int i = 0; i < left;i++){
                int t = i;
                revs[i] = revData.size();
                for(int j = 0; j < left;j++){
                    if(i != j){
                        if(matrix[t] != 0)revData.add(j);
                    }
                    t+= left;
                }
            }
            revs[left] = revData.size();
            int[] revData2 = new int[revData.size()];
            for(int i  = 0; i < revData2.length;i++){
                revData2[i] = revData.get(i);
            }
            
            revData.clear();
            revData = null;
    		// slot value means: 0: undep, 1: dep, 2: dep and visited
    		if(targetInvolved == 0 && !ret[ret.length - left - 1].equals(targetCls))targetInvolved = -1; // -1 means somehow it wont work
    		// ok, now we need to calculate the matrix
    		while(stack.size() > 0){
    		    int v = stack.pop();
    		    int f = v >> 16;
                v &= 0xFFFF;
                if(matrix[f * left + v] == 1)matrix[f * left + v] = 2;
                else continue; // already visited
                int st = revs[f];
                int ed = revs[f+1];
                while(st < ed){
                    int f2 = revData2[st] * left + v;
                    if(matrix[f2] == 0){
                        matrix[f2] = 1;
                        stack.add((revData2[st] << 16) | v);
                    }
                    st++;
                }
    		}
    		
//    		while(true){
//    			int c = nn.size();
//    			for(int i = 0; i < left;i++){
//    				for(int j = 0; j < left;j++){
//    					if(i == j)continue;
//    					String key = target[i] + "-" + target[j];
//    					if(nn.contains(key))continue;
//    					HashSet<String> deps = mapp.get(target[i]);
//    					if(deps == null){
//    						int g = 0;
//    					}
//    					if(deps.contains(target[j]))continue; // never happened
//    					chkCnt = 0;
//    					for(String dep : deps){
//    						if(nn.contains(dep + "-" + target[j])){
//    							chkCnt = 1;
//    							break;
//    						}
//    					}
//    					if(chkCnt == 1){
//    						nn.add(key); //
//							deps.add(target[j]);
//    					}
//    				}
//    			}
//    			if(nn.size() == c)break; // end of the loop
//    		}
    		// split the circles
    		ArrayList<ArrayList<String>> circles = new ArrayList<ArrayList<String>>();
    		LinkedHashMap<String,String> circleIds = new LinkedHashMap<String,String>();
    		for(int i = 0; i < left;i++){
    			//HashSet<String> deps = mapp.get(target[i]);
    			ArrayList<String> circle = null;
    			String circleName = circleIds.get(target[i]);
    			if(circleName == null){
    			    int t = i * left;
    			    HashSet<String> deps = new HashSet<String>();
    			    for(int k = 0;k < left;k++){
    			        if(k != i){
    			            if(matrix[t+k] != 0){
    			                String dep = target[k];
    			                if(!circleIds.containsKey(dep) && matrix[k*left + i] != 0){
    			                    if(circle == null)circle = new ArrayList<String>();
    	                            circle.add(dep);
    	                            matrix[t+k] = 3; // mark it as circle link
    			                }else{
    			                    deps.add(dep);  
    			                }
    			            }
    			        }
    			    }
	    			//for(String dep : deps){
	    			//	if(!circleIds.containsKey(dep) && nn.contains(dep + "-" + target[i])){
	    			//		// a loop
	    			//		if(circle == null)circle = new ArrayList<String>();
	    			//		circle.add(dep);
	    			//	}
	    			//}
    			
	    			if(circle != null){
	    				circleName = circles.size() + ""; // a circle name
	    				circles.add(circle);
	    				for(String c : circle){
	    					//deps.remove(c);
	    					circleIds.put(c,circleName);
	    				}
	    				circle.add(target[i]);
	    				circleIds.put(target[i],circleName);
	    				mapp.remove(target[i]);
	    				//if(deps.size() < 1)deps = null;
	    				
	    				mapp.put(circleName,deps);
	    			}
    			}else{
    				HashSet<String> cdeps = mapp.get(circleName);
    				int t = i * left;
    				for(int k = 0; k < left;k++){
    				    if(k != i){
    				        if(matrix[t+k] != 0){ 
    				            String dep = target[k];
    				            if(!circleIds.containsKey(dep)){
    	                            if(cdeps == null){
    	                                mapp.put(circleName, cdeps = new HashSet<String>());
    	                            }
    	                            cdeps.add(dep);
    	                        }
    				        }
    				    }
    				}
    				//for(String dep : deps){
    				//	if(!circleIds.containsKey(dep)){
    				//		if(cdeps == null){
    				//			mapp.put(circleName, cdeps = new HashSet<String>());
    				//		}
    				//		cdeps.add(dep);
    				//	}
    				//}
    				mapp.remove(target[i]);
    			}
    			
    		}
    		// update circles
    		HashSet<String> nn = new HashSet<String>();
    		for(String key : mapp.keySet()){
    			HashSet<String> deps = mapp.get(key);
    			nn.clear();
    			for(String dep : deps){
    				String crid = circleIds.get(dep);
    				if(crid != null){
    					nn.add(crid);
    				}else
    					nn.add(dep);
    			}
    			deps.clear();
    			deps.addAll(nn);
    		}
    		// FIX: put it into a array for keys, put targetCls at last
    		chks = new String[mapp.size()];
            mapp.keySet().toArray(chks);
            if(targetInvolved == 2){ // involved 
                String tcid = circleIds.get(targetCls);
                if(tcid == null)tcid = targetCls;
                else{
                    // also make sure the target is the last
                    ArrayList<String> circle = circles.get(Integer.parseInt(tcid));
                    int find = -1;
                    for(int i  =0; i < circle.size();i++){
                        if(circle.get(i).equals(targetCls)){
                            find = i;
                            break;
                        }
                    }
                    if(find >= 0){
                        circle.remove(find);
                        circle.add(targetCls);
                    }
                }
                if(!chks[chks.length -1].equals(targetCls)){
                    for(int i  =0; i < chks.length;i++){
                        if(chks[i].equals(targetCls)){
                            chks[i] = chks[chks.length-1];
                            chks[chks.length-1] = targetCls;
                            break;
                        }
                    }
                }
            }
    		// finalize the order, simply based on the stuff we have
    		while(left > 0){
    			cnt = 0;
    			for(int i =0; i < chks.length;i++){
    			    String key = chks[i];
    			    if(targetInvolved == 2 && cnt > 0 && i == chks.length - 1)continue;
    				HashSet<String> p = mapp.get(key);
    				if(p == null)continue;
    				if(p.size() > 0){
        				nn.clear();
        				for(String k : p){
        					if(mapp.get(k) != null){
        						nn.add(k);
        					}
        				}
        				p.clear();
        				if(nn.size() > 0){
        					p.addAll(nn);
        				}
        			}
    				if(p.size() < 1){
        				if(Character.isDigit(key.charAt(0))){
        					// it's a circle
        					int idx = Integer.parseInt(key);
        					ArrayList<String> c = circles.get(idx);
        					if(c.size() > 1){
        					 // FIX: we still need to sort it based on inheritance order
        					    Collections.sort(c, new Sorter(inherMap));
        					}
        					for(String k : c){
        						ret[ret.length - left - (targetInvolved == 0 ? 1 : 0)] = "*" + k;
        						left--;
        					}
        				}else{
        					ret[ret.length - left - (targetInvolved == 0 ? 1 : 0)] = key;
    						left--;
        				}
        				cnt++;
        				mapp.put(key, null);
        				if(left < 1)break;
        			}
    			}
    			if(cnt == 0)break;
    		}
    		
    		if(targetInvolved == 0){ // no depenedncy, put the target at last
    		    ret[ret.length - left - 1] = targetCls; // fill back in
    		}
    	}
    	if(left > 0)throw new Exception("dame, something is wrong with the dependecy check!");
    	return ret;
    }
}
