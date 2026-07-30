#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

struct ready_message {
    pid_t browser_pid;
    pid_t receiver_pid;
};

static const char *output_path = "/tmp/hedgeyos-seqpacket-result.txt";
static const char *mode = "normal";
static int attempts_limit = 16;
static int timeout_seconds = 5;

static void die(const char *message) {
    perror(message);
    exit(2);
}

static void write_result(int success, int eof, int enosys_count,
                         int other_errno, int attempts) {
    char temporary[512];
    snprintf(temporary, sizeof(temporary), "%s.tmp.%ld",
             output_path, (long)getpid());
    FILE *output = fopen(temporary, "w");
    if (!output) {
        _exit(3);
    }
    fprintf(output,
            "mode=%s\nreceiver_pid=%ld\nrecv_success=%d\neof=%d\n"
            "enosys_count=%d\nother_errno=%d\nattempts=%d\n",
            mode, (long)getpid(), success, eof, enosys_count,
            other_errno, attempts);
    if (fclose(output) != 0 || rename(temporary, output_path) != 0) {
        _exit(3);
    }
}

static void receiver_main(int socket_fd) {
    int success = 0;
    int eof = 0;
    int enosys_count = 0;
    int other_errno = 0;
    int attempts = 0;

    for (; attempts < attempts_limit; attempts++) {
        char payload[16] = {0};
        char control[CMSG_SPACE(sizeof(int))] = {0};
        struct iovec iov = {
            .iov_base = payload,
            .iov_len = sizeof(payload),
        };
        struct msghdr message = {
            .msg_iov = &iov,
            .msg_iovlen = 1,
            .msg_control = control,
            .msg_controllen = sizeof(control),
        };
        ssize_t received = recvmsg(socket_fd, &message, 0);
        if (received > 0) {
            success++;
            struct cmsghdr *header = CMSG_FIRSTHDR(&message);
            if (header && header->cmsg_level == SOL_SOCKET &&
                header->cmsg_type == SCM_RIGHTS) {
                int received_fd;
                memcpy(&received_fd, CMSG_DATA(header), sizeof(received_fd));
                close(received_fd);
            }
            continue;
        }
        if (received == 0) {
            eof = 1;
            attempts++;
            break;
        }
        if (errno == EINTR) {
            attempts--;
            continue;
        }
        if (errno == ENOSYS) {
            enosys_count++;
            struct timespec delay = {.tv_sec = 0, .tv_nsec = 10000000};
            nanosleep(&delay, NULL);
            continue;
        }
        other_errno = errno;
        attempts++;
        break;
    }

    write_result(success, eof, enosys_count, other_errno, attempts);
    close(socket_fd);
    _exit(enosys_count ? 38 : (success == 1 && eof && !other_errno ? 0 : 4));
}

static void send_descriptor(int socket_fd) {
    int descriptor = open("/dev/null", O_RDONLY);
    if (descriptor < 0) {
        die("open /dev/null");
    }
    char payload = 'H';
    char control[CMSG_SPACE(sizeof(int))] = {0};
    struct iovec iov = {
        .iov_base = &payload,
        .iov_len = sizeof(payload),
    };
    struct msghdr message = {
        .msg_iov = &iov,
        .msg_iovlen = 1,
        .msg_control = control,
        .msg_controllen = sizeof(control),
    };
    struct cmsghdr *header = CMSG_FIRSTHDR(&message);
    header->cmsg_level = SOL_SOCKET;
    header->cmsg_type = SCM_RIGHTS;
    header->cmsg_len = CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(header), &descriptor, sizeof(descriptor));
    if (sendmsg(socket_fd, &message, 0) != 1) {
        die("sendmsg");
    }
    close(descriptor);
}

static void browser_main(int ready_fd) {
    int sockets[2];
    if (socketpair(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0, sockets) != 0) {
        die("socketpair");
    }
    pid_t receiver = fork();
    if (receiver < 0) {
        die("fork receiver");
    }
    if (receiver == 0) {
        close(ready_fd);
        close(sockets[0]);
        receiver_main(sockets[1]);
    }

    close(sockets[1]);
    send_descriptor(sockets[0]);
    struct ready_message ready = {
        .browser_pid = getpid(),
        .receiver_pid = receiver,
    };
    if (write(ready_fd, &ready, sizeof(ready)) != (ssize_t)sizeof(ready)) {
        die("write ready");
    }
    close(ready_fd);

    if (strcmp(mode, "browser-abort") == 0) {
        _exit(42);
    }
    if (strcmp(mode, "browser-kill") == 0) {
        for (;;) {
            pause();
        }
    }

    close(sockets[0]);
    int status;
    if (waitpid(receiver, &status, 0) < 0) {
        die("wait receiver");
    }
    _exit(WIFEXITED(status) ? WEXITSTATUS(status) : 5);
}

static bool result_exists(void) {
    struct stat status;
    return stat(output_path, &status) == 0 && status.st_size > 0;
}

static int result_enosys_count(void) {
    FILE *input = fopen(output_path, "r");
    if (!input) {
        return -1;
    }
    char line[128];
    int count = -1;
    while (fgets(line, sizeof(line), input)) {
        if (sscanf(line, "enosys_count=%d", &count) == 1) {
            break;
        }
    }
    fclose(input);
    return count;
}

static int result_contract_passes(void) {
    FILE *input = fopen(output_path, "r");
    if (!input) {
        return 0;
    }
    char line[128];
    int success = 0;
    int eof = 0;
    int other_errno = -1;
    while (fgets(line, sizeof(line), input)) {
        sscanf(line, "recv_success=%d", &success);
        sscanf(line, "eof=%d", &eof);
        sscanf(line, "other_errno=%d", &other_errno);
    }
    fclose(input);
    return success == 1 && eof == 1 && other_errno == 0;
}

static void usage(const char *program) {
    fprintf(stderr,
            "usage: %s [--mode normal|browser-abort|browser-kill] "
            "[--output path] [--attempts count] [--timeout seconds]\n",
            program);
    exit(2);
}

int main(int argc, char **argv) {
    for (int index = 1; index < argc; index++) {
        if (strcmp(argv[index], "--mode") == 0 && index + 1 < argc) {
            mode = argv[++index];
        } else if (strcmp(argv[index], "--output") == 0 && index + 1 < argc) {
            output_path = argv[++index];
        } else if (strcmp(argv[index], "--attempts") == 0 && index + 1 < argc) {
            attempts_limit = atoi(argv[++index]);
        } else if (strcmp(argv[index], "--timeout") == 0 && index + 1 < argc) {
            timeout_seconds = atoi(argv[++index]);
        } else {
            usage(argv[0]);
        }
    }
    if (strcmp(mode, "normal") != 0 &&
        strcmp(mode, "browser-abort") != 0 &&
        strcmp(mode, "browser-kill") != 0) {
        usage(argv[0]);
    }
    unlink(output_path);

    int ready_pipe[2];
    if (pipe2(ready_pipe, O_CLOEXEC) != 0) {
        die("pipe2");
    }
    pid_t browser = fork();
    if (browser < 0) {
        die("fork browser");
    }
    if (browser == 0) {
        close(ready_pipe[0]);
        browser_main(ready_pipe[1]);
    }
    close(ready_pipe[1]);

    struct ready_message ready;
    ssize_t ready_bytes = read(ready_pipe[0], &ready, sizeof(ready));
    close(ready_pipe[0]);
    if (ready_bytes != (ssize_t)sizeof(ready)) {
        kill(browser, SIGKILL);
        waitpid(browser, NULL, 0);
        fprintf(stderr, "reproducer did not receive process identities\n");
        return 2;
    }
    if (strcmp(mode, "browser-kill") == 0) {
        kill(ready.browser_pid, SIGKILL);
    }
    waitpid(browser, NULL, 0);

    for (int elapsed = 0; elapsed < timeout_seconds * 100; elapsed++) {
        if (result_exists()) {
            break;
        }
        struct timespec delay = {.tv_sec = 0, .tv_nsec = 10000000};
        nanosleep(&delay, NULL);
    }
    if (!result_exists()) {
        kill(ready.receiver_pid, SIGKILL);
        fprintf(stderr,
                "reproducer timed out; receiver pid %ld was contained\n",
                (long)ready.receiver_pid);
        return 124;
    }

    int enosys_count = result_enosys_count();
    if (enosys_count > 0) {
        fprintf(stderr, "recvmsg returned ENOSYS %d time(s)\n", enosys_count);
        return 38;
    }
    if (!result_contract_passes()) {
        fprintf(stderr, "recvmsg lifecycle contract failed\n");
        return 4;
    }
    return 0;
}
