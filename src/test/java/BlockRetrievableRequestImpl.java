import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.SystemConnection;
import com.github.ibm.mapepire.requests.BlockRetrievableRequest;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BlockRetrievableRequestImpl extends BlockRetrievableRequest {
    protected BlockRetrievableRequestImpl(DataStreamProcessor _io, SystemConnection _conn, JsonObject _reqObj) {
        super(_io, _conn, _reqObj);
    }

    public DataBlockFetchResult getNextDataBlockUsage(final ResultSet _rs, final int _numRows,
                     final boolean _isTerseDataFormat) throws SQLException, IOException {
        return getNextDataBlock(_rs, _numRows, _isTerseDataFormat);
    }
    @Override
    protected void go() throws Exception {
    }
}
