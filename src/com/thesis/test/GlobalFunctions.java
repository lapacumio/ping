package com.thesis.test;

public class GlobalFunctions {
	
    public static int convertToInt(int size){
    	switch(size){
			case(160):
				return 1;
			case(100):
				return 2;
			case(1000):
				return 3;
			case(10000):
				return 4;
			case(100000):
				return 5;
			default:
				return size;
    	}
    }
    public static int convertToSize(int code){
    	switch(code){
        case(1):
			return 160;
		case(2):
			return 100;
		case(3):
			return 1000;
		case(4):
			return 10000;
		case(5):
			return 100000;
		default:
			return code;
    	}
    }
	
}
