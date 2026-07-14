# Contributing to cloud-itonami-isco-3134

Thanks for your interest in contributing!

## Getting started

1. Fork the repository
2. Clone your fork locally
3. Create a feature branch: `git checkout -b my-feature`
4. Make your changes and commit them: `git commit -am 'Add my feature'`
5. Push to your fork: `git push origin my-feature`
6. Submit a pull request

## Code style

- Follow Clojure style conventions (e.g., using kebab-case for function names)
- All source code is portable `.cljc` (no JVM-only constructs)
- Tests are required for new features
- Run `clojure -M:lint` before submitting a PR

## Testing

Make sure all tests pass:
```bash
clojure -M:test
```

## Security Issues

If you discover a security vulnerability, please email security@example.com instead of using the issue tracker. Please do not publicly disclose the issue until it has been addressed.
