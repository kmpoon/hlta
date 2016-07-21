package hk.ust.cse.lantern.data.io;

import hk.ust.cse.lantern.data.Data;

public interface Reader {
	public Data read() throws Exception;
}
