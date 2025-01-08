/*
 * cmode.cpp
 *
 *  Created on: 06.01.2025
 *      Author: marvi
 */

#include "de_m_marvin_cmode_Console.h"
#include <stdio.h>

#ifdef OS_WIN
#include <windows.h>
static HANDLE handleIn = NULL;
static HANDLE handleOut = NULL;
static DWORD defaultInMode = 0;
static DWORD defaultOutMode = 0;
#elif OS_LIN

#endif

#define DEFAULT 0
#define ANSI 1
#define ANSI_EVENTS 2

JNIEXPORT jboolean JNICALL Java_de_m_1marvin_cmode_Console_setMode_1n(JNIEnv *env, jclass clazz, jint mode) {

#ifdef OS_WIN
	if (handleIn == NULL || handleOut == NULL) {
		handleIn = GetStdHandle(STD_INPUT_HANDLE);
		if (handleIn == INVALID_HANDLE_VALUE) return 0;
		handleOut = GetStdHandle(STD_OUTPUT_HANDLE);
		if (handleOut == INVALID_HANDLE_VALUE) return 0;
		if (!GetConsoleMode(handleIn, &defaultInMode)) return 0;
		if (!GetConsoleMode(handleOut, &defaultOutMode)) return 0;
	}

	switch (mode) {
	case DEFAULT:

		// Default mode, what was previously configured
		if (!SetConsoleMode(handleIn, defaultInMode)) return 0;
		if (!SetConsoleMode(handleOut, defaultOutMode)) return 0;

		break;
	case ANSI:

		// ANSI/VT100 mode, enable VT100 processing on input and output streams
		if (!SetConsoleMode(handleIn, ENABLE_VIRTUAL_TERMINAL_INPUT | ENABLE_PROCESSED_INPUT)) return 0;
		if (!SetConsoleMode(handleOut, ENABLE_PROCESSED_OUTPUT | ENABLE_VIRTUAL_TERMINAL_PROCESSING)) return 0;

		break;
	case ANSI_EVENTS:

		// ANSI/VT100 mode plus support for some additional events (may be platform specific)
		if (!SetConsoleMode(handleIn, ENABLE_VIRTUAL_TERMINAL_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_WINDOW_INPUT | ENABLE_MOUSE_INPUT)) return 0;
		if (!SetConsoleMode(handleOut, ENABLE_PROCESSED_OUTPUT | ENABLE_VIRTUAL_TERMINAL_PROCESSING)) return 0;

		break;
	}

#elif OS_LIN

#endif

	return 1;

}
