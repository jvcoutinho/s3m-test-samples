package com.pa.util;

public enum EnumPublicationLocalType {
	PERIODIC("Peri�dico"),
	CONFERENCE("Confer�ncia");
	
	private int number = 23;
	
	private EnumPublicationLocalType(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public String getNameNumber() {
		return name + this.number ;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return this.name;
	}
}
