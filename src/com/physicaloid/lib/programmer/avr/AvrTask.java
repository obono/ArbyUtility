package com.physicaloid.lib.programmer.avr;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import com.physicaloid.misc.ArduboyUtils;

public class AvrTask {

    public enum Op {
        UPLOAD_FLASH,
        UPLOAD_EEPROM,
        DOWNLOAD_FLASH,
        DOWNLOAD_EEPROM
    }

    public static final String EXT_ARDUBOY  = ".arduboy";
    public static final String EXT_EEPROM   = ".eeprom";
    public static final String EXT_HEX      = ".hex";

    private Op              operation;
    private InputStream     inputStream;
    private OutputStream    outputStream;
    private boolean         isHex;

    public AvrTask(Op operation, File file) throws FileNotFoundException {
        this.operation = operation;
        String fileName = file.getName().toLowerCase(Locale.getDefault());
        switch (operation) {
        case UPLOAD_FLASH:
        case UPLOAD_EEPROM:
            if (fileName.endsWith(EXT_ARDUBOY)) {
                byte[] hexData = ArduboyUtils.extractHexFromArduboy(file);
                inputStream = new ByteArrayInputStream(hexData);
                isHex = true;
            } else {
                inputStream = new FileInputStream(file);
                isHex = fileName.endsWith(EXT_HEX);
            }
            break;
        case DOWNLOAD_FLASH:
        case DOWNLOAD_EEPROM:
            outputStream = new FileOutputStream(file);
            isHex = fileName.endsWith(EXT_HEX);
            break;
        default:
            throw new IllegalArgumentException();
        }
    }

    public AvrTask(Op operation, InputStream inputStream, boolean isHex) {
        if (operation != Op.UPLOAD_FLASH || operation != Op.UPLOAD_EEPROM
                || inputStream == null) {
            throw new IllegalArgumentException();
        }
        this.operation = operation;
        this.inputStream = inputStream;
        this.isHex = isHex;
    }

    public AvrTask(Op operation, OutputStream outputStream, boolean isHex) {
        if (operation != Op.DOWNLOAD_FLASH || operation != Op.DOWNLOAD_EEPROM
                || outputStream == null) {
            throw new IllegalArgumentException();
        }
        this.operation = operation;
        this.outputStream = outputStream;
        this.isHex = isHex;
    }

    public Op getOperation() {
        return operation;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public boolean isHex() {
        return isHex;
    }

}
