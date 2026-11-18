// Counts the OpenGL draw calls the game makes. It sits between LWJGL and the real OpenGL library: LWJGL looks every
// GL function up through glXGetProcAddress, and this hands back a counting wrapper for the draw functions.
// Every 50 ms it appends "<unix time in ms> <draw calls so far>" to the file named by GLCOUNT_FILE.
// How to build and use it: tools/uitest/README.md, "Measuring how much a screen costs".
#define _GNU_SOURCE
#include <dlfcn.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

typedef void *(*gpa_t)(const unsigned char *);
static gpa_t real_gpa;
static atomic_long draws;

typedef void (*de_t)(unsigned, int, unsigned, const void *);
typedef void (*da_t)(unsigned, int, int);
typedef void (*dei_t)(unsigned, int, unsigned, const void *, int);
typedef void (*dai_t)(unsigned, int, int, int);
typedef void (*dre_t)(unsigned, unsigned, unsigned, int, unsigned, const void *);
typedef void (*mde_t)(unsigned, const int *, unsigned, const void *const *, int);
static de_t real_de;
static da_t real_da;
static dei_t real_dei;
static dai_t real_dai;
static dre_t real_dre;
static mde_t real_mde;

static void my_de(unsigned m, int c, unsigned t, const void *i) { atomic_fetch_add(&draws, 1); real_de(m, c, t, i); }
static void my_da(unsigned m, int f, int c) { atomic_fetch_add(&draws, 1); real_da(m, f, c); }
static void my_dei(unsigned m, int c, unsigned t, const void *i, int n) { atomic_fetch_add(&draws, 1); real_dei(m, c, t, i, n); }
static void my_dai(unsigned m, int f, int c, int n) { atomic_fetch_add(&draws, 1); real_dai(m, f, c, n); }
static void my_dre(unsigned m, unsigned s, unsigned e, int c, unsigned t, const void *i) { atomic_fetch_add(&draws, 1); real_dre(m, s, e, c, t, i); }
static void my_mde(unsigned m, const int *c, unsigned t, const void *const *i, int n) { atomic_fetch_add(&draws, n); real_mde(m, c, t, i, n); }

static void *writer(void *arg) {
    const char *path = (const char *) arg;
    for (;;) {
        struct timespec ts;
        clock_gettime(CLOCK_REALTIME, &ts);
        FILE *f = fopen(path, "a");
        if (f) {
            fprintf(f, "%ld %ld\n", (long) ts.tv_sec * 1000 + ts.tv_nsec / 1000000, atomic_load(&draws));
            fclose(f);
        }
        usleep(50000);
    }
    return NULL;
}

__attribute__((constructor)) static void init(void) {
    void *gl = dlopen("libGL.so.1", RTLD_NOW | RTLD_GLOBAL);
    if (gl) real_gpa = (gpa_t) dlsym(gl, "glXGetProcAddressARB");
    const char *path = getenv("GLCOUNT_FILE");
    if (path) {
        pthread_t t;
        pthread_create(&t, NULL, writer, strdup(path));
        pthread_detach(t);
    }
}

// Both exported names go through this one function: if glXGetProcAddress called glXGetProcAddressARB, the call
// would reach libGL's own export instead of this library's, and nothing would be counted.
static void *lookup(const unsigned char *name) {
    void *p = real_gpa ? real_gpa(name) : NULL;
    if (!p) return p;
    const char *n = (const char *) name;
    if (!strcmp(n, "glDrawElements")) { real_de = (de_t) p; return (void *) my_de; }
    if (!strcmp(n, "glDrawArrays")) { real_da = (da_t) p; return (void *) my_da; }
    if (!strcmp(n, "glDrawElementsInstanced")) { real_dei = (dei_t) p; return (void *) my_dei; }
    if (!strcmp(n, "glDrawArraysInstanced")) { real_dai = (dai_t) p; return (void *) my_dai; }
    if (!strcmp(n, "glDrawRangeElements")) { real_dre = (dre_t) p; return (void *) my_dre; }
    if (!strcmp(n, "glMultiDrawElements")) { real_mde = (mde_t) p; return (void *) my_mde; }
    return p;
}

void *glXGetProcAddressARB(const unsigned char *name) { return lookup(name); }
void *glXGetProcAddress(const unsigned char *name) { return lookup(name); }
