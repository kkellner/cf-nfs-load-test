package net.hyperboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CreateNfsTraffic {

	private static Logger logger = LoggerFactory.getLogger(CreateNfsTraffic.class);

	// @Value("${vcap.application.application_id:APP_ID}")

	@Value("${vcap.application.application_name:APP_NAME}")
	private String cfAppName;

	@Value("${CF_INSTANCE_INDEX:0}")
	private int cfInstanceIndex;

	// How often a log message will be output with the bytes read/written per second
	@Value("${logOutputIntervalSeconds}")
	long logOutputIntervalSeconds;

	// Delay in milliseconds before read and/or write test starts
	@Value("${startupDelay}")
	long startupDelay;

	@Value("${vcap.services.test-nfs.volume_mounts[0].container_dir:#{null}}")
	String nfsDirectory;

	// ********************
	// Read properties
	// ********************

	@Value("${read.threads}")
	int readThreads;

	@Value("${read.filename}")
	String testReadFileBaseName;

	@Value("${read.chunkSize}")
	int readChunkSize;

	@Value("${read.filesize}")
	long readFileSize;

	@Value("${read.durationSeconds}")
	long readDurationSeconds;

	// ********************
	// Write properties
	// ********************

	@Value("${write.threads}")
	int writeThreads;

	@Value("${write.filename}")
	String testWriteFileBaseName;

	@Value("${write.chunkSize}")
	int writeChuckSize;

	@Value("${read.filesize}")
	long writeFileSize;

	@Value("${write.durationSeconds}")
	long writeDurationSeconds;

	private boolean shutdown = false;

	private ExecutorService readExecutor;
	private ExecutorService writeExecutor;

	@PostConstruct
	public void setUp() {
		if (nfsDirectory == null) {
			String msg = "NFS directory not defined -- is an NFS service named 'test-nfs' bound to app?";
			logger.error(msg);
			throw new Error(msg);
		}
		logger.info("NFS directory: {}", nfsDirectory);

	}

	// https://gitlab.com/jamesmarkchan/jDiskMark/-/blob/master/src/jdiskmark/DiskWorker.java
	// https://github.com/giraone/java-io-benchmark-sample/blob/master/src/main/java/com/giraone/samples/io/IoBenchmark.java

	public void begin() {
		try {
			// Sleep for at least 1 second before we start
			try {
				Thread.sleep(Math.max(1000, startupDelay));
			} catch (InterruptedException e) {
				return;
			}

			if (readThreads > 0) {
				readExecutor = Executors.newFixedThreadPool(readThreads);
				for (int i = 0; i < readThreads; i++) {
					int instanceNum = i;
					readExecutor.execute(() -> {
						this.readDataTest(instanceNum);
					});
				}
			}

			if (writeThreads > 0) {
				writeExecutor = Executors.newFixedThreadPool(writeThreads);
				for (int i = 0; i < writeThreads; i++) {
					int instanceNum = i;
					writeExecutor.execute(() -> {
						this.writeDataTest(instanceNum);
					});
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Set flag to indicate the async threads should stop
	 */
	public void stop() {
		shutdown = true;
		if (readExecutor != null) {
			readExecutor.shutdown();
		}
		if (writeExecutor != null) {
			writeExecutor.shutdown();
		}
	}

	public void readDataTest(int instanceNum) {

		String baseName = String.format("%s-%s-i%d", testReadFileBaseName, cfAppName, cfInstanceIndex);
		String baseThreadName = String.format("%s-t%d", baseName, instanceNum);

		if (instanceNum == 0) {
			logger.warn("WARNING: app will read data from base file {} in directory {} for {} seconds", baseName,
					nfsDirectory, readDurationSeconds);
		}

		try {
			File dataFile = new File(nfsDirectory, baseThreadName);
			// Create a file to use for the read test
			dataFile.delete();
			writeData(dataFile, readFileSize, 8 * 1024);
			readDataForDuration(dataFile, readFileSize, readChunkSize, readDurationSeconds * 1000);
			dataFile.delete();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void writeDataTest(int instanceNum) {

		String baseName = String.format("%s-%s-i%d", testWriteFileBaseName, cfAppName, cfInstanceIndex);
		String baseThreadName = String.format("%s-t%d", baseName, instanceNum);

		if (instanceNum == 0) {
			logger.warn("WARNING: app will write to base file {} in directory {} for {} seconds", baseName,
					nfsDirectory, writeDurationSeconds);
		}

		try {
			File dataFile = new File(nfsDirectory, baseThreadName);
			writeDataForDuration(dataFile, writeFileSize, writeChuckSize, writeDurationSeconds * 1000);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Write random bytes to file. Deletes the file (if it exists) before bytes are
	 * written.
	 * 
	 * @param dataFile
	 * @param fileSize
	 * @param loopDuration zero do not loop
	 * @throws IOException
	 */
	public void writeDataForDuration(File dataFile, long fileSize, int chuckSize, long loopDurationMilliseconds)
			throws IOException {

		dataFile.delete();

		long now = System.currentTimeMillis();
		long writeStartTime = now;
		long lastLogTime = now;
		long bytesWrittenPerInterval = 0;
		do {

			bytesWrittenPerInterval += writeData(dataFile, fileSize, chuckSize);

			now = System.currentTimeMillis();
			long duration = now - lastLogTime;
			if (duration > (logOutputIntervalSeconds * 1000)) {
				long writtenPerSecond = (long)(bytesWrittenPerInterval / ((float)duration / 1000));
				logger.info("Bytes written per second: {}", String.format("%,d", writtenPerSecond));
				lastLogTime = System.currentTimeMillis();
				bytesWrittenPerInterval = 0;
			}

			if (shutdown) {
				break;
			}

		} while (System.currentTimeMillis() - writeStartTime < loopDurationMilliseconds);

		dataFile.delete();
		
		if (loopDurationMilliseconds > 0) {
			logger.info("write data complete after {} seconds", (System.currentTimeMillis() - writeStartTime) / 1000);
		}

	}

	/**
	 * Write bytes
	 * 
	 * @param dataFile
	 * @param fileSize
	 * @param chuckSize
	 * @return Number of bytes written
	 * @throws IOException
	 */
	public long writeData(File dataFile, long fileSize, int chuckSize) throws IOException {

		FileOutputStream out = null;
		long bytesWritten = 0;

		byte[] randomData = new byte[chuckSize];
		new Random().nextBytes(randomData);

		try {

			// We purposely don't use a bufferedOutputStream because we're trying
			// to generate a lot of network traffic to test NFS load on NSX
			out = new FileOutputStream(dataFile);

			while (bytesWritten < fileSize) {
				int len = (int) Math.min(chuckSize, fileSize - bytesWritten);
				out.write(randomData, 0, len);
				out.flush();
				bytesWritten += len;
				if (shutdown) {
					break;
				}
			}

		} finally {
			IOUtils.closeQuietly(out);
		}
		return bytesWritten;

	}

	/**
	 * Read bytes from file.
	 * 
	 * @param dataFile
	 * @param fileSize
	 * @param loopDuration zero do not loop
	 * @throws IOException
	 */
	public void readDataForDuration(File dataFile, long fileSize, int chuckSize, long loopDurationMilliseconds)
			throws IOException {

		long now = System.currentTimeMillis();
		long readStartTime = now;
		long lastLogTime = now;
		long bytesPerInterval = 0;
		do {

			bytesPerInterval += readData(dataFile, fileSize, chuckSize);

			now = System.currentTimeMillis();
			long duration = now - lastLogTime;
			if (duration > (logOutputIntervalSeconds * 1000)) {
				long readPerSecond = (long)(bytesPerInterval / ((float)duration / 1000));
				logger.info("Bytes read per second: {}", String.format("%,d", readPerSecond));
				lastLogTime = System.currentTimeMillis();
				bytesPerInterval = 0;
			}

			if (shutdown) {
				break;
			}

		} while (System.currentTimeMillis() - readStartTime < loopDurationMilliseconds);

		if (loopDurationMilliseconds > 0) {
			logger.info("read data complete after {} seconds", (System.currentTimeMillis() - readStartTime) / 1000);
		}

	}
	

	/**
	 * Read bytes
	 * 
	 * @param dataFile
	 * @param fileSize
	 * @param chuckSize
	 * @return Number of bytes read
	 * @throws IOException
	 */
	public long readData(File dataFile, long fileSize, int chuckSize) throws IOException {

		FileInputStream in = null;
		long totalBytesRead = 0;

		byte[] data = new byte[chuckSize];

		try {

			// We purposely don't use a bufferedOutputStream because we're trying
			// to generate a lot of network traffic to test NFS load on NSX
			in = new FileInputStream(dataFile);

			while (totalBytesRead < fileSize) {
				int len = (int) Math.min(chuckSize, fileSize - totalBytesRead);
				int bytesRead = in.read(data, 0, len);
				if (bytesRead < 0) {
					logger.warn("Read past end of stream");
					break;
				}
				if (bytesRead != len) {
					logger.warn("Read returned {} bytes when {} bytes was requested.", bytesRead, len);
				}
				totalBytesRead += bytesRead;
				if (shutdown) {
					break;
				}
			}

		} finally {
			IOUtils.closeQuietly(in);
		}
		return totalBytesRead;

	}


}
