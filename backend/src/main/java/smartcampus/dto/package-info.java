/**
 * Request and response data transfer objects.
 *
 * <p>DTOs form the public shape of the API and keep persistence entities out of the wire
 * format. Request DTOs carry Jakarta Bean Validation constraints; response DTOs expose
 * only fields a caller is allowed to see — notably, no password or token hash ever
 * appears in a response DTO.
 */
package smartcampus.dto;
