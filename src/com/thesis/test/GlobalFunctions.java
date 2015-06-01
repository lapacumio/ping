package com.thesis.test;

public class GlobalFunctions {
	
    public static int convertToInt(int size){
    	switch(size){
			case(160):
				return 101;
			case(100):
				return 102;
			case(1000):
				return 103;
			case(10000):
				return 104;
			case(100000):
				return 105;
			default:
				return size;
    	}
    }
    public static int convertToSize(int code){
    	switch(code){
        case(101):
			return 160;
		case(102):
			return 100;
		case(103):
			return 1000;
		case(104):
			return 10000;
		case(105):
			return 100000;
		default:
			return code;
    	}
    }
	
}
