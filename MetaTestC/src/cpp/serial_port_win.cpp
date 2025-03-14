
#ifdef PLATFORM_WIN

#include "serial_port.hpp"
#include <windows.h>
#include <thread>
#include <chrono>
#include <stdio.h>

class SerialPortWin : public SerialPort {

private:
	DCB comPortState;
	COMMTIMEOUTS comPortTimeouts;
	HANDLE comPortHandle;
	const char* portFileName;

public:

	SerialPortWin(const char* portFile)
	{
		this->portFileName = portFile;
		this->comPortHandle = INVALID_HANDLE_VALUE;
		this->comPortState = {0};
		this->comPortTimeouts = {0};
	}

	~SerialPortWin() {
		closePort();
	}

	void setConfig(const SerialPortConfig &config) {
		if (this->comPortHandle == INVALID_HANDLE_VALUE) return;

		GetCommState(this->comPortHandle, &this->comPortState);
		this->comPortState.BaudRate = config.baudRate;
		this->comPortState.fBinary = TRUE;
		this->comPortState.fParity = (config.parity != SPC_PARITY_NONE);
		this->comPortState.fOutxCtsFlow = (config.flowControl == SPC_FLOW_RTS_CTS);
		this->comPortState.fOutxDsrFlow = (config.flowControl == SPC_FLOW_DSR_DTR);
		this->comPortState.fDtrControl = (config.flowControl == SPC_FLOW_DSR_DTR) ? DTR_CONTROL_ENABLE : DTR_CONTROL_DISABLE;
		this->comPortState.fDsrSensitivity = (config.flowControl == SPC_FLOW_DSR_DTR);
		this->comPortState.fTXContinueOnXoff = (config.flowControl == SPC_FLOW_NONE);
		this->comPortState.fOutX = (config.flowControl == SPC_FLOW_XON_XOFF);
		this->comPortState.fInX = (config.flowControl == SPC_FLOW_XON_XOFF);
		this->comPortState.fErrorChar = 0;
		this->comPortState.fNull = 0;
		this->comPortState.fRtsControl = (config.flowControl == SPC_FLOW_RTS_CTS) ? RTS_CONTROL_TOGGLE : RTS_CONTROL_ENABLE;
		this->comPortState.fAbortOnError = 0;
		this->comPortState.XonLim = 2048;
		this->comPortState.XoffLim = 512;
		this->comPortState.ByteSize = config.dataBits;
		switch (config.parity) {
		case SPC_PARITY_NONE: this->comPortState.Parity = NOPARITY; break;
		case SPC_PARITY_ODD: this->comPortState.Parity = ODDPARITY; break;
		case SPC_PARITY_EVEN: this->comPortState.Parity = EVENPARITY; break;
		case SPC_PARITY_MARK: this->comPortState.Parity = MARKPARITY; break;
		case SPC_PARITY_SPACE: this->comPortState.Parity = SPACEPARITY; break;
		default: break;
		}
		switch (config.stopBits) {
		case SPC_STOPB_ONE: this->comPortState.StopBits = ONESTOPBIT; break;
		case SPC_STOPB_ONE_HALF: this->comPortState.StopBits = ONE5STOPBITS; break;
		case SPC_STOPB_TWO: this->comPortState.StopBits = TWOSTOPBITS; break;
		default: break;
		}
		this->comPortState.XonChar = 17;
		this->comPortState.XoffChar = 19;
		this->comPortState.ErrorChar = 0;
		this->comPortState.EofChar = 0;
		this->comPortState.EvtChar = 0;
		SetCommState(this->comPortHandle, &this->comPortState);

	}

	void getConfig(SerialPortConfig &config) {
		if (this->comPortHandle == INVALID_HANDLE_VALUE) return;

		GetCommState(this->comPortHandle, &this->comPortState);
		config.baudRate = this->comPortState.BaudRate;
		if (this->comPortState.fParity == 0) {
			config.parity = SPC_PARITY_NONE;
		} else {
			switch (this->comPortState.Parity) {
			case NOPARITY: config.parity = SPC_PARITY_NONE; break;
			case ODDPARITY: config.parity = SPC_PARITY_ODD; break;
			case EVENPARITY: config.parity = SPC_PARITY_EVEN; break;
			case MARKPARITY: config.parity = SPC_PARITY_MARK; break;
			case SPACEPARITY: config.parity = SPC_PARITY_SPACE; break;
			default: config.parity = SPC_PARITY_UNDEFINED; break;
			}
		}
		config.dataBits = this->comPortState.ByteSize;
		switch (this->comPortState.StopBits) {
		case ONESTOPBIT: config.stopBits = SPC_STOPB_ONE; break;
		case ONE5STOPBITS: config.stopBits = SPC_STOPB_ONE_HALF; break;
		case TWOSTOPBITS: config.stopBits = SPC_STOPB_TWO; break;
		default: config.stopBits = SPC_STOPB_UNDEFINED; break;
		}
		if (this->comPortState.fOutX && this->comPortState.fInX) {
			config.flowControl = SPC_FLOW_XON_XOFF;
		} else if (this->comPortState.fOutxCtsFlow && !this->comPortState.fOutxDsrFlow) {
			config.flowControl = SPC_FLOW_RTS_CTS;
		} else if (!this->comPortState.fOutxCtsFlow && this->comPortState.fOutxDsrFlow) {
			config.flowControl = SPC_FLOW_DSR_DTR;
		} else {
			config.flowControl = SPC_FLOW_UNDEFINED;
		}
	}

	bool openPort()
	{
		if (this->comPortHandle != INVALID_HANDLE_VALUE) return false;
		this->comPortHandle = CreateFileA(this->portFileName, GENERIC_WRITE | GENERIC_READ, 0, NULL, OPEN_EXISTING, 0, NULL);

		if (isOpen()) {
			setConfig(DEFAULT_PORT_CONFIGURATION);
			return true;
		}

		return false;
	}

	void closePort()
	{
		if (this->comPortHandle == INVALID_HANDLE_VALUE) return;
		CloseHandle(this->comPortHandle);
		this->comPortHandle = INVALID_HANDLE_VALUE;
	}

	bool isOpen()
	{
		return this->comPortHandle != INVALID_HANDLE_VALUE;
	}

	void setBaud(unsigned long baud)
	{
		if (this->comPortHandle == INVALID_HANDLE_VALUE) return;
		GetCommState(this->comPortHandle, &this->comPortState);
		this->comPortState.BaudRate = baud;
		SetCommState(this->comPortHandle, &this->comPortState);
	}

	int getBaud()
	{
		if (this->comPortHandle == INVALID_HANDLE_VALUE) return 0;
		GetCommState(this->comPortHandle, &this->comPortState);
		return this->comPortState.BaudRate;
	}

	void setTimeouts(unsigned int readTimeout, unsigned int writeTimeout)
	{
		GetCommTimeouts(this->comPortHandle, &this->comPortTimeouts);
		this->comPortTimeouts.ReadIntervalTimeout = readTimeout == 0 ? MAXDWORD : 0;
		this->comPortTimeouts.ReadTotalTimeoutConstant = readTimeout;
		this->comPortTimeouts.ReadTotalTimeoutMultiplier = 0;
		this->comPortTimeouts.WriteTotalTimeoutConstant = writeTimeout;
		this->comPortTimeouts.WriteTotalTimeoutMultiplier = 0;
		SetCommTimeouts(this->comPortHandle, &this->comPortTimeouts);
	}

	unsigned long readBytes(char* buffer, unsigned long bufferCapacity)
	{
		if (this->comPortHandle == INVALID_HANDLE_VALUE) return 0;
		unsigned long receivedBytes = 0;
		if (!ReadFile(this->comPortHandle, buffer, bufferCapacity, &receivedBytes, NULL)) return 0;
		return receivedBytes;
	}

	unsigned long readBytesConsecutive(char* buffer, unsigned long bufferCapacity, unsigned int consecutiveDelay, unsigned int receptionWaitTimeout)
	{
		if (this->comPortHandle == INVALID_HANDLE_VALUE) return 0;
		unsigned long receivedBytes;
		long long waitStart = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
		while ((receivedBytes = readBytes(buffer, bufferCapacity)) == 0) {
			long long time = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
			if (time - waitStart > receptionWaitTimeout) return 0;
		}
		while (receivedBytes < bufferCapacity)
		{
			std::this_thread::sleep_for(std::chrono::milliseconds(consecutiveDelay));
			unsigned int lastReceived = readBytes(buffer + receivedBytes, bufferCapacity - receivedBytes);
			if (lastReceived == 0) break;
			receivedBytes += lastReceived;
		}
		return receivedBytes;
	}

	unsigned long writeBytes(const char* buffer, unsigned long bufferLength)
	{
		if (this->comPortHandle == INVALID_HANDLE_VALUE) return 0;
		unsigned long writtenBytes;
		WriteFile(this->comPortHandle, buffer, bufferLength, &writtenBytes, NULL);
		return writtenBytes;
	}


};

SerialPort* newSerialPort(const char* portFile) {
	return new SerialPortWin(portFile);
}

SerialPort* newSerialPort(const std::string& portFile) {
	return new SerialPortWin(portFile.c_str());
}

#endif
