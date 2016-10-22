package soot.util;

import java.util.Map;
import java.util.Set;

public abstract class AbstractMultiMap<K, V> implements MultiMap<K, V> {
	
    @Override
    public void putAll( MultiMap<K,V> m ) {
        for (K key : m.keySet())
            putAll(key, m.get(key));
    }
    
    @Override
    public void putAll( Map<K,Set<V>> m ) {
        for (K key : m.keySet())
            putAll(key, m.get(key));
    }
    
    @Override
    public boolean isEmpty() {
        return numKeys() == 0;
    }
    
    @Override
	public boolean contains(K key, V value) {
		Set<V> set = get(key);
		if (set == null)
			return false;
		return set.contains(value);
	}

    public String toString() {
    	StringBuilder sb = new StringBuilder();
    	sb.append("{");
    	boolean first = true;
    	for(K key : this.keySet()) {
    		if(first) {
    			first = false;
    		} else {
    			sb.append(',');
    		}
    		sb.append(key.toString());
    		sb.append("=");
    		sb.append(this.get(key).toString());
    	}
    	sb.append("}");
    	return sb.toString();
    }
}
