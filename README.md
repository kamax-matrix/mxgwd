# Matrix Gateway

A gateway that aims to:
- Control what users can do with the Matrix API, depending on roles backed by an extended Identity server.
- Control what federation calls are allowed and from which server(s)
- Filter and/or transform endpoints

This gateway wishes to be client and server agnostic, so policies can be consistently enforced across implementations.
