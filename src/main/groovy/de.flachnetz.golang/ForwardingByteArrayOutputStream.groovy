class ForwardingByteArrayOutputStream extends ByteArrayOutputStream {
    OutputStream target

    @Override
    synchronized void write(int b) {
        target.write(b)
        super.write(b)
    }

    @Override
    synchronized void write(byte[] b, int off, int len) {
        target.write(b, off, len)
        super.write(b, off, len)
    }
}
