import com.github.ibm.mapepire.DataStreamProcessor;
import com.github.ibm.mapepire.requests.BlockRetrievableRequest;
import com.github.ibm.mapepire.ws.BinarySender;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import javax.sql.rowset.serial.SerialBlob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class BlobTest {

    @Test
    public void testMockResultSetSingleRow() throws SQLException {
        // Arrange
        Map<String, Object> row = new HashMap<>();
        List<List<Object>> rowsByIndex;

        row.put("id", 1);
        row.put("name", "Alice");

        List<Map<String, Object>> rows = Collections.singletonList(row);
        ResultSet rs = new MockResultSet(rows, null);

        // Act & Assert
        assertTrue(rs.next(), "Expected at least one row");
        assertEquals(1, rs.getInt("id"));
        assertEquals("Alice", rs.getString("name"));

        assertFalse(rs.next(), "No more rows expected");
    }

    @Test
    public void testMockResultSetMultipleRows() throws SQLException {
        // Arrange
        Map<String, Object> row1 = new HashMap<>();
        row1.put("id", 1);
        row1.put("name", "Alice");

        Map<String, Object> row2 = new HashMap<>();
        row2.put("id", 2);
        row2.put("name", "Bob");

        List<Map<String, Object>> rows = Arrays.asList(row1, row2);
        ResultSet rs = new MockResultSet(rows, null);

        // First row
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("Alice", rs.getString("name"));

        // Second row
        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));
        assertEquals("Bob", rs.getString("name"));

        // No more
        assertFalse(rs.next());
    }

    @Test
    void testBlobColumnInResultSet() throws Exception {
        // Create some fake binary data
        byte[] data = "hello world".getBytes();

        // Wrap it in a Blob
        Blob blob = new SerialBlob(data);

        List<List<Object>> rowsByIndex = Arrays.asList(Arrays.asList(blob));


        // Use MockResultSet to simulate a DB result
        MockResultSet rs = new MockResultSet(rowsByIndex);

        // Act: move to the first row
        assertTrue(rs.next());

        // Retrieve the blob
        Blob retrievedBlob = rs.getBlob(1);
        assertNotNull(retrievedBlob);

        // Read the bytes back
        byte[] retrievedData = retrievedBlob.getBytes(1, (int) retrievedBlob.length());

        // Verify the round-trip matches
        assertArrayEquals(data, retrievedData);

        // No more rows
        assertFalse(rs.next());
    }

    @Test
    public void testGetBinaryStreamByIndex() throws SQLException, IOException {
        // Prepare blob data
        byte[] blobData = {1, 2, 3, 4, 5};

        // Use rowsByIndex to support getBinaryStream(int columnIndex)
        List<List<Object>> rowsByIndex = new ArrayList<>();
        rowsByIndex.add(Arrays.asList("Alice", new ByteArrayInputStream(blobData)));

        MockResultSet rs = new MockResultSet(rowsByIndex);

        assertTrue(rs.next(), "Should have first row");

        InputStream is = rs.getBinaryStream(2); // second column
        assertNotNull(is, "BinaryStream should not be null");

        byte[] readData = is.readAllBytes();
        assertArrayEquals(blobData, readData, "Binary data should match");
    }

    @Test
    public void testSendingSingleBlob() throws SQLException, IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "12345");
        BinarySender binarySender = mock(BinarySender.class);
        final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, binarySender, null, true);


        BlockRetrievableRequestImpl block = new BlockRetrievableRequestImpl(io, null, obj);
        byte[] blobData = {1, 2, 3, 4, 5};
        byte[] expectedResult = {5, 49, 50, 51, 52, 53, 0, 4, 98, 108, 111, 98, 5, 1, 2, 3, 4, 5}; // queryIdLength | queryId | rowId | colNameLength | colName| bloblLength| blob
        Blob blob = new SerialBlob(blobData);

        // Use rowsByIndex to support getBinaryStream(int columnIndex)
        List<List<Object>> rowsByIndex = new ArrayList<>();
        rowsByIndex.add(Arrays.asList(blob));

        Map<String, Object> row1 = new HashMap<>();
        row1.put("blob", blob);
        List<Map<String, Object>> rows = Arrays.asList(row1);
        MockResultSet rs = new MockResultSet(rows, rowsByIndex);
        block.getNextDataBlockUsage(rs, 1, false);

        verify(binarySender, times(1)).send(any(ByteBuffer.class), anyBoolean());

        // Assert - capture arguments
        ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        ArgumentCaptor<Boolean> booleanCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(binarySender, times(1)).send(bufferCaptor.capture(), booleanCaptor.capture());

        // Verify first call
        ByteBuffer firstBuf = bufferCaptor.getAllValues().get(0);
        Boolean firstFlag = booleanCaptor.getAllValues().get(0);
        byte[] actual = Arrays.copyOfRange(firstBuf.array(), firstBuf.position(), firstBuf.limit());
        assertArrayEquals(expectedResult, actual);
        assertTrue(firstFlag);

    }


}
