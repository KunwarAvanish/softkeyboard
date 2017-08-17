package com.example.android.softkeyboard;


import java.util.Vector;

public class StringToken {
	
	
	public static String[] spliteMethods(String splitStr, String delimiter){
		
		StringBuffer token= new StringBuffer();
		
		Vector<String> tokens=new Vector<String>();
		char[] chars=splitStr.toCharArray();
		
		for(int i=0;i<chars.length;i++){
			
		 if(delimiter.indexOf(chars[i])!=-1)
		 {
			if(token.length()>0){
				
				tokens.addElement(token.toString());
				token.setLength(0);
			}
		}
		 
		 else
		 {
			 token.append(chars[i]);
		 }
		
	}

		if(token.length()>0){
			 
			tokens.addElement(token.toString());
		}
		
		String[] splitArray=new String[tokens.size()];
		
		for(int i=0;i<splitArray.length;i++){
			
			splitArray[i]=(String)tokens.elementAt(i);
		}
		
		return splitArray;
}
	
	
}