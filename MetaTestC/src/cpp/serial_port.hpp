#pragma once

#include <string>

#ifdef PLATFORM_WIN
#define LIB_EXPORT __declspec(dllexport)
#define LIB_IMPORT __declspec(dllimport)
#else
#define LIB_EXPORT
#define LIB_IMPORT
#endif

typedef enum SerialPortParities {
	SPC_PARITY_NONE = 1,
	SPC_PARITY_ODD = 2,
	SPC_PARITY_EVEN = 3,
	SPC_PARITY_MARK = 4,
	SPC_PARITY_SPACE = 5,
	SPC_PARITY_UNDEFINED = 0
} SerialPortParity;

enum SerialPortFlowControl {
	SPC_FLOW_NONE = 1,
	SPC_FLOW_XON_XOFF = 2,
	SPC_FLOW_RTS_CTS = 3,
	SPC_FLOW_DSR_DTR = 4,
	SPC_FLOW_UNDEFINED = 0
};

enum SerialPortStopBits {
	SPC_STOPB_ONE = 1,
	SPC_STOPB_ONE_HALF = 2,
	SPC_STOPB_TWO = 3,
	SPC_STOPB_UNDEFINED = 0
};

typedef struct SerialPortConfiguration {
	unsigned int baudRate;
	unsigned char dataBits;
	SerialPortStopBits stopBits;
	SerialPortParity parity;
	SerialPortFlowControl flowControl;
} SerialPortConfig;

static const SerialPortConfig DEFAULT_PORT_CONFIGURATION = {
	.baudRate = 9200,
	.dataBits = 8,
	.stopBits = SPC_STOPB_ONE,
	.parity = SPC_PARITY_NONE,
	.flowControl = SPC_FLOW_XON_XOFF
};

class SerialPort
{

public:
	virtual LIB_EXPORT ~SerialPort() {};

	/**
	 * Applies the supplied configuration to the port.
	 * The port has to be open for this to work.
	 * @param config The configuration struct to apply
	 */
	virtual LIB_EXPORT void setConfig(const SerialPortConfig &config) = 0;

	/**
	 * Reads the current configuration of the port.
	 * The port has to be open for this to work.
	 * @param config The configuration struct to write the configuration to
	 */
	virtual LIB_EXPORT void getConfig(SerialPortConfig &config) = 0;

	/**
	 * Sets the baud rate for the port, this is equal to doing it using setConfig().
	 * The port has to be open for this to work.
	 * @param baud The baud rate to set for the port
	 */
	virtual LIB_EXPORT void setBaud(unsigned long baud) = 0;

	/**
	 * Gets the current baud rate configured for the port, equal to doing it using getConfig().
	 * The port has to be open for this to work.
	 * @return The current baud configured for the port, or 0 if the port is not open
	 */
	virtual LIB_EXPORT int getBaud() = 0;

	/**
	 * Sets the read write timeouts for the port.
	 * Zero means no timeout.
	 * The port has to be open for this to work.
	 * @param readTimeout The read timeout, if the requested amount of data is not received within this time, it returns with what it has (might be zero)
	 * @param writeTimeout The write timout, if the supplied data could not be written within this time, it returns with the ammount of data that could be written (might be zero)
	 */
	virtual LIB_EXPORT void setTimeouts(unsigned int readTimeout, unsigned int writeTimeout) = 0;

	/**
	 * Attempt to claim/open the port.
	 * This might fail if the port was not found or is already clamed by an another process.
	 * @return true if the port was successfully opened, false otherwise
	 */
	virtual LIB_EXPORT bool openPort() = 0;

	/**
	 * Closes the port.
	 * If the port is already closed, this has no affect.
	 */
	virtual LIB_EXPORT void closePort() = 0;

	/**
	 * Returns true if the port is open and can be written to or read from.
	 * @return true if the port is open, false otherwise
	 */
	virtual LIB_EXPORT bool isOpen() = 0;

	/**
	 * Attempts to fill the buffer by reading bytes from the port.
	 * If not enough bytes could be read after the read timeout expires, the number of bytes read is returned.
	 * @param buffer The buffer to write the data to
	 * @param bufferCapacity The capacity of the buffer, aka the max number of bytes to read
	 */
	virtual LIB_EXPORT unsigned long readBytes(char* buffer, unsigned long bufferCapacity) = 0;

	/**
	 * Attempts to fill the buffer by reading bytes from the port.
	 * The difference to the normal read is, that the timeout is extended each time a byte is received.
	 * This is useful if the data arrives in random intervals, such as user input.
	 *
	 * NOTE
	 * The consecutive delay is implemented as delay between the read operations.
	 * This means that the application will always wait for the configured ammount of time before attempting to read the next bytes.
	 * So the value should be held small enough to not cause a buffer overflow.
	 *
	 * NOTE
	 * The reception wait timeout can not be lower than the read timeout of the port, lower values will simply mean to use the read timeout
	 * The actual timeout will also always be round up to the next multiple of the read timeout.
	 *
	 * @param buffer The buffer to write the data to
	 * @param bufferCapacity The capacity of the buffer, aka the max number of bytes to read
	 * @param consecutiveDelay The timeout to add when data was received
	 * @param receptionWaitTimeout The minimum time to wait for the first byte
	 */
	virtual LIB_EXPORT unsigned long readBytesConsecutive(char* buffer, unsigned long bufferCapacity, unsigned int consecutiveDelay, unsigned int receptionWaitTimeout) = 0;

	/**
	 * Attempts to write the content of the buffer to the serial port.
	 * Id not all data could be written until the write timeout expires, the number of bytes written is returned.
	 * @param buffer The buffer to read the data from
	 * @param bufferLength The length of the buffer, aka the number of bytes to write
	 */
	virtual LIB_EXPORT unsigned long writeBytes(const char* buffer, unsigned long bufferLength) = 0;
	
};

extern "C" {

void testFunc();

SerialPort* newSerialPort(const char* portFile);
//SerialPort* newSerialPort(const std::string& portFile);

}