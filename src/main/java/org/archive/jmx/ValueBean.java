package org.archive.jmx;

public class ValueBean {
	public ValueBean(long s, float v) {
		MSeconds = s;
		Value = v;
	}
	public ValueBean() {
		MSeconds = -1; /* null */
		Value = 0;
	}
	public long MSeconds;
	public float Value;
}