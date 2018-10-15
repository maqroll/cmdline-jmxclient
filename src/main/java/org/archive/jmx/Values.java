package org.archive.jmx;

import java.util.ArrayList;
import java.util.List;

public class Values {
	private static final int PROFUNDIDAD = 10;

	private float min = 0;
	private float max = 100;  
			
	public Values() {
		values = new ValueBean[PROFUNDIDAD];
		ptr = -1;
		for (int i = 0; i< PROFUNDIDAD; i++) {
			values[i] = new ValueBean();
		}
	}
	
	public List<ValueBean> get(long from, long to) {
		List<ValueBean> r = new ArrayList<ValueBean>();
		
		if (ptr >=0) { /* si hay algun valor */
			int myptr = ptr;
			long last = values[myptr /*Ultimo valor insertado */].MSeconds;
			
			do {
				if (values[myptr].MSeconds < from) break;
				if ((values[myptr].MSeconds >= from) && (values[myptr].MSeconds <= to)) {
					r.add(new ValueBean(values[myptr].MSeconds,values[myptr].Value));
				}
				myptr = (myptr + PROFUNDIDAD - 1) % PROFUNDIDAD;
			} while (values[myptr].MSeconds > 0 && values[myptr].MSeconds < last); 
		}
		
		return r;
	}
	
	public void add(String v) {
		ValueBean value = values[(ptr+1) % PROFUNDIDAD];
		value.Value = new Float(v).floatValue();
		if (value.Value < min) min = value.Value;
		if (value.Value > max) max = value.Value;
		value.MSeconds = System.currentTimeMillis();
		ptr = ++ptr % PROFUNDIDAD;
	}
	
	public float getMax() {
		return max;
	}
	
	public float getMin() {
		return min;
	}
	
	ValueBean[] values;
	int ptr;
}
