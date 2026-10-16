package dev.campuspilot.api.exception;

/**
 * The caller is authenticated but not allowed to do this.
 *
 * <p>403, not 404. The distinction matters: 401 means "log in", 403 means
 * "you are logged in and it still is not yours".
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public static ForbiddenException notYourSubject() {
        return new ForbiddenException("You do not teach this subject");
    }

    public static ForbiddenException studentsCannotMarkAttendance() {
        return new ForbiddenException(
                "Attendance is recorded by faculty, not by students");
    }

    public static ForbiddenException facultyOnly() {
        return new ForbiddenException("This action requires a faculty or admin account");
    }
}