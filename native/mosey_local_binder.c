/* Process-local compatibility experiment. Keep the binder at the default
 * system stability level instead of declaring a cross-partition VINTF HAL.
 * No system policy, service manager, or other process is patched. */
__attribute__((visibility("default")))
void AIBinder_markVintfStability(void *binder) {
    (void)binder;
}
