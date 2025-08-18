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

//    @Test
//    public void testGetBinaryStreamByIndex() throws SQLException, IOException {
//        // Prepare blob data
//        byte[] blobData = {1, 2, 3, 4, 5};
//
//        // Use rowsByIndex to support getBinaryStream(int columnIndex)
//        List<List<Object>> rowsByIndex = new ArrayList<>();
//        rowsByIndex.add(Arrays.asList("Alice", new ByteArrayInputStream(blobData)));
//
//        MockResultSet rs = new MockResultSet(rowsByIndex);
//
//        assertTrue(rs.next(), "Should have first row");
//
//        InputStream is = rs.getBinaryStream(2); // second column
//        assertNotNull(is, "BinaryStream should not be null");
//
//        byte[] readData = is.readAllBytes();
//        assertArrayEquals(blobData, readData, "Binary data should match");
//    }

    @Test
    public void testSendingSingleRowWithBlob() throws SQLException, IOException {
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

    @Test
    public void testSendingMultipleRowsWithBlobs() throws SQLException, IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "12345");
        BinarySender binarySender = mock(BinarySender.class);
        final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, binarySender, null, true);


        BlockRetrievableRequestImpl block = new BlockRetrievableRequestImpl(io, null, obj);
        byte[] blob1Data = {1, 2, 3, 4, 5};
        byte[] blob2Data = {6, 7, 8};

        byte[] expectedResult1 = {5, 49, 50, 51, 52, 53, 0, 4, 98, 108, 111, 98, 5, 1, 2, 3, 4, 5}; // queryIdLength | queryId | rowId | colNameLength | colName| bloblLength| blob
        byte[] expectedResult2 = {1, 4, 98, 108, 111, 98, 3, 6, 7, 8}; //  rowId | colNameLength | colName| bloblLength| blob

        Blob blob1 = new SerialBlob(blob1Data);
        Blob blob2 = new SerialBlob(blob2Data);

        // Use rowsByIndex to support getBinaryStream(int columnIndex)
        List<List<Object>> rowsByIndex = new ArrayList<>();
        rowsByIndex.add(Arrays.asList(blob1));
        rowsByIndex.add(Arrays.asList(blob2));

        Map<String, Object> row1 = new HashMap<>();
        Map<String, Object> row2 = new HashMap<>();

        row1.put("blob", blob1);
        row2.put("blob", blob2);

        List<Map<String, Object>> rows = Arrays.asList(row1, row2);
        MockResultSet rs = new MockResultSet(rows, rowsByIndex);
        block.getNextDataBlockUsage(rs, 2, false);

        verify(binarySender, times(2)).send(any(ByteBuffer.class), anyBoolean());

        // Assert - capture arguments
        ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        ArgumentCaptor<Boolean> booleanCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(binarySender, times(2)).send(bufferCaptor.capture(), booleanCaptor.capture());

        // Verify first call
        ByteBuffer firstBuf = bufferCaptor.getAllValues().get(0);
        Boolean firstFlag = booleanCaptor.getAllValues().get(0);
        byte[] actual = Arrays.copyOfRange(firstBuf.array(), firstBuf.position(), firstBuf.limit());
        assertArrayEquals(expectedResult1, actual);
        assertFalse(firstFlag);

        // Verify second call
        ByteBuffer secondBuf = bufferCaptor.getAllValues().get(1);
        Boolean secondFlag = booleanCaptor.getAllValues().get(1);
        byte[] actualSecond = Arrays.copyOfRange(secondBuf.array(), secondBuf.position(), secondBuf.limit());
        assertArrayEquals(expectedResult2, actualSecond);
        assertTrue(secondFlag);
    }


    @Test
    public void testSingleRowMultipleBlobs() throws SQLException, IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "123");
        BinarySender binarySender = mock(BinarySender.class);
        final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, binarySender, null, true);


        BlockRetrievableRequestImpl block = new BlockRetrievableRequestImpl(io, null, obj);
        byte[] blobData1 = {10, 11};
        byte[] blobData2 = {20, 21, 22};
        Blob blob1 = new SerialBlob(blobData1);
        Blob blob2 = new SerialBlob(blobData2);



        byte[] expected1 = {
                3, '1','2','3', // queryId
                0,              // rowId
                1, 'a',         // colName "a"
                2, 10, 11,      // blob
        };

        byte[] expected2 = {
                0,              // rowId
                2, 'b','b',     // colName "bb"
                3, 20, 21, 22   // blob
        };
        // Use rowsByIndex to support getBinaryStream(int columnIndex)
        List<List<Object>> rowsByIndex = new ArrayList<>();
        rowsByIndex.add(Arrays.asList(blob1, blob2));

        Map<String, Object> row1 = new HashMap<>();

        row1.put("a", blob1);
        row1.put("bb", blob2);

        List<Map<String, Object>> rows = Arrays.asList(row1);
        MockResultSet rs = new MockResultSet(rows, rowsByIndex);
        block.getNextDataBlockUsage(rs, 1, false);

        verify(binarySender, times(2)).send(any(ByteBuffer.class), anyBoolean());

        // Assert - capture arguments
        ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        ArgumentCaptor<Boolean> booleanCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(binarySender, times(2)).send(bufferCaptor.capture(), booleanCaptor.capture());

        // Verify first call
        ByteBuffer firstBuf = bufferCaptor.getAllValues().get(0);
        Boolean firstFlag = booleanCaptor.getAllValues().get(0);
        byte[] actual = Arrays.copyOfRange(firstBuf.array(), firstBuf.position(), firstBuf.limit());
        assertArrayEquals(expected1, actual);
        assertFalse(firstFlag);

        // Verify second call
        ByteBuffer secondBuf = bufferCaptor.getAllValues().get(1);
        Boolean secondFlag = booleanCaptor.getAllValues().get(1);
        byte[] actualSecond = Arrays.copyOfRange(secondBuf.array(), secondBuf.position(), secondBuf.limit());
        assertArrayEquals(expected2, actualSecond);
        assertTrue(secondFlag);
    }

    @Test
    public void testMultipleRowsMultipleBlobsPerRow() throws SQLException, IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "query7");
        BinarySender binarySender = mock(BinarySender.class);
        final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, binarySender, null, true);


        BlockRetrievableRequestImpl block = new BlockRetrievableRequestImpl(io, null, obj);
        byte[] blob1Data = {1, 2, 3, 4, 5};
        byte[] blob2Data = {6, 7, 8};

        byte[] expected1 = {
                6, 'q', 'u', 'e', 'r', 'y', '7', // queryId
                0,              // rowId
                11, 'b', 'l','o','b','C','o','l','u','m','n','1',         // colName "a"
                5, 1, 2, 3, 4 ,5      // blob
        };

        byte[] expected2 = {
                0,              // rowId
                11, 'b', 'l','o','b','C','o','l','u','m','n','2',
                3, 6, 7, 8   // blob
        };

        byte[] expected3 = {
                1,              // rowId
                11, 'b', 'l','o','b','C','o','l','u','m','n','1',
                5, 1, 2, 3, 4 ,5      // blob
        };

        byte[] expected4 = {
                1,              // rowId
                11, 'b', 'l','o','b','C','o','l','u','m','n','2',
                3, 6, 7, 8   // blob
        };


        Blob blob1 = new SerialBlob(blob1Data);
        Blob blob2 = new SerialBlob(blob2Data);

        // Use rowsByIndex to support getBinaryStream(int columnIndex)
        List<List<Object>> rowsByIndex = new ArrayList<>();
        rowsByIndex.add(Arrays.asList(blob1, blob2));
        rowsByIndex.add(Arrays.asList(blob1, blob2));

        Map<String, Object> row1 = new HashMap<>();
        Map<String, Object> row2 = new HashMap<>();

        row1.put("blobColumn1", blob1);
        row1.put("blobColumn2", blob2);

        row2.put("blobColumn1", blob1);
        row2.put("blobColumn2", blob2);


        List<Map<String, Object>> rows = Arrays.asList(row1, row2);
        MockResultSet rs = new MockResultSet(rows, rowsByIndex);
        block.getNextDataBlockUsage(rs, 2, false);

        verify(binarySender, times(4)).send(any(ByteBuffer.class), anyBoolean());

        // Assert - capture arguments
        ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        ArgumentCaptor<Boolean> booleanCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(binarySender, times(4)).send(bufferCaptor.capture(), booleanCaptor.capture());

        // Verify first call
        ByteBuffer firstBuf = bufferCaptor.getAllValues().get(0);
        Boolean firstFlag = booleanCaptor.getAllValues().get(0);
        byte[] actual = Arrays.copyOfRange(firstBuf.array(), firstBuf.position(), firstBuf.limit());
        assertArrayEquals(expected1, actual);
        assertFalse(firstFlag);

        // Verify second call
        ByteBuffer secondBuf = bufferCaptor.getAllValues().get(1);
        Boolean secondFlag = booleanCaptor.getAllValues().get(1);
        byte[] actualSecond = Arrays.copyOfRange(secondBuf.array(), secondBuf.position(), secondBuf.limit());
        assertArrayEquals(expected2, actualSecond);
        assertFalse(secondFlag);

        // Verify third call
        ByteBuffer thirdBuf = bufferCaptor.getAllValues().get(2);
        Boolean thirdFlag = booleanCaptor.getAllValues().get(2);
        byte[] actualThird = Arrays.copyOfRange(thirdBuf.array(), thirdBuf.position(), thirdBuf.limit());
        assertArrayEquals(expected3, actualThird);
        assertFalse(thirdFlag);

        // Verify fourth call
        ByteBuffer fourthBuf = bufferCaptor.getAllValues().get(3);
        Boolean fourthFlag = booleanCaptor.getAllValues().get(3);
        byte[] actualFourth = Arrays.copyOfRange(fourthBuf.array(), fourthBuf.position(), fourthBuf.limit());
        assertArrayEquals(expected4, actualFourth);
        assertTrue(fourthFlag);
    }


    @Test
    public void testSendingSingleRowWithEmptyBlob() throws SQLException, IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "12345");
        BinarySender binarySender = mock(BinarySender.class);
        final DataStreamProcessor io = new DataStreamProcessor(System.in, System.out, binarySender, null, true);


        BlockRetrievableRequestImpl block = new BlockRetrievableRequestImpl(io, null, obj);
        byte[] blobData = {};
        Blob blob = new SerialBlob(blobData);

        // Use rowsByIndex to support getBinaryStream(int columnIndex)
        List<List<Object>> rowsByIndex = new ArrayList<>();
        rowsByIndex.add(Arrays.asList(blob));

        Map<String, Object> row1 = new HashMap<>();
        row1.put("blob", blob);
        List<Map<String, Object>> rows = Arrays.asList(row1);
        MockResultSet rs = new MockResultSet(rows, rowsByIndex);
        block.getNextDataBlockUsage(rs, 1, false);

        verify(binarySender, times(0)).send(any(ByteBuffer.class), anyBoolean());

        // Assert - capture arguments
        ArgumentCaptor<ByteBuffer> bufferCaptor = ArgumentCaptor.forClass(ByteBuffer.class);
        ArgumentCaptor<Boolean> booleanCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(binarySender, times(0)).send(bufferCaptor.capture(), booleanCaptor.capture());

    }


}
