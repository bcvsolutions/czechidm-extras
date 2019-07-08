package eu.bcvsolutions.idm.extras.utils;

public class Pair<U,V> {

	private U first;
	private V second;

	public Pair(U first, V second){
		this.first = first;
		this.second = second;
	}

	public U getFirst() {
		return first;
	}

	public void setFirst(U first) {
		this.first = first;
	}

	public V getSecond() {
		return second;
	}

	public void setSecond(V second) {
		this.second = second;
	}

	@Override
	public boolean equals(Object o){
		if(o instanceof Pair){
			return ((Pair) o).first.equals(this.first) && ((Pair) o).second.equals(this.second);
		}
		return false;
	}
}
