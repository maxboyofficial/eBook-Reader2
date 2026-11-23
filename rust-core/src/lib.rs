// src/lib.rs
// Minimal JNI bridge for proof of concept.

use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jstring, jbyteArray};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_example_data_reader_RustParser_parseEpub(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) -> jstring {
    // For now just return a placeholder string.
    let placeholder = "Parsed EPUB (placeholder)";
    let output = env.new_string(placeholder).expect("Failed to create Java string");
    output.into_inner()
}

#[no_mangle]
pub extern "system" fn Java_com_example_data_reader_RustParser_renderPdfPage(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
    page: i32,
) -> jbyteArray {
    // Return the same byte array unchanged as a stub.
    // In a real implementation, we would render the PDF page and return PNG bytes.
    data.into_inner()
}
