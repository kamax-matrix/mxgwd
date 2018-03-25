# mxgwd - Matrix Policy Gateway Daemon
[![Build Status](https://travis-ci.org/kamax-io/mxgwd.svg?branch=master)](https://travis-ci.org/kamax-io/mxgwd)

- [Overview](#overview)
- [Concepts](#concepts)
- [Getting Started](#getting-started)
- [Support](#support)

## Overview
mxgwd is a gateway/reverse proxy for the Matrix REST API that aims to:
- Control what users can do with the Matrix API, depending on roles backed by an extended Identity server.
- Control what federation calls are allowed and from which server(s).
- Filter and/or transform endpoints.
- Perform additional actions for matching requests.

mxgwd wishes to be client and server agnostic, so policies can be consistently applied across implementations.

**THIS SOFTWARE IS CURRENTLY ALPHA, USAGE IN PRODUCTION IS DISCOURAGED**

## Concepts
mxgwd processes requests of configured hosts by matching endpoints/actions and processing ACLs linked to them.
If all applicable ACLs allow the request, it is then sent to the backend.

### Host
A host matches the `host:port` of the requested [URL](https://en.wikipedia.org/wiki/URL#Syntax).  
There is currently no catch-all or wildcard/regexp support, and each host must be fully configured.

If a request's host is not matched in the configuration, it will be denied.

### Endpoint
An endpoint matches either:
- A well-identified action from the client, which is translated to the appropriate HTTP method and URL path
- A custom HTTP method and/or URL path (matched with `startsWith()`)

The following configuration keys are available:
- `action` to match Actions
- `method` to match HTTP methods
- `path` to match URL paths
- `to` to overwrite the target URL defined at the host level

#### Actions
As actions represent well-defined messages and/or endpoints from the Matrix specification, the default Matrix naming scheme
has been kept which is based on the Java packages naming scheme.

The following actions are available:
- `m.room.create`: When a client attempts to create a room.

### ACL
ACLs are the rules applied to a matched endpoint, allowing or disallowing the request.

#### Types
##### Whitelist
With: `whitelist`

Grant access if the condition is true.

##### Blacklist
With: `blacklist`

Deny access if the condition is true.

#### Targets
##### HTTP Method
With: `method`

The HTTP method value (e.g. `GET`, `POST`, etc.), if the provided value is a match.

##### Authenticated user's groups
With: `group`
  
The groups the authenticated user belongs to, if the provided value matches one of the groups in the list.
  
This ACL type will deny access if one of the following conditions is true:
- The client did not provide an access token
- The Gateway is unable to validate the access token
- The access token is not valid
- The Gateway is not configured with an Identity server that supports the appropriate endpoint
- The Gateway is unable to retrieve the list of groups.

## Getting started
### Requirements
- Java JRE 8+ to run, JDK to build
- Reverse proxy for TLS/SSL support

### Reverse Proxy
By default, the gateway will listen on port `8007` of all IPs. To change, add the following line to your config file:
```yaml
server.port: <HTTP port to listen to>
```

Configure your reverse proxy to send all `/_matrix` request to it.

#### Apache 2
In the `VirtualHost` section handling the domain with SSL, add the following and replace `0.0.0.0` by the internal
hostname/IP pointing to the gateway.

**This line MUST replace the one for the homeserver and MUST be after the one to your Identity server, if applicable.**
```
ProxyPass /_matrix http://0.0.0.0:8007/_matrix
```

Typical configuration would look like:
```
<VirtualHost *:443>
    ServerName example.org
    ...
    
    ProxyPreserveHost on
    ...
    
    ProxyPass /_matrix http://localhost:8007/_matrix
</VirtualHost>
```

#### nginx
In the `server` section handling the domain with SSL, add the following and replace `0.0.0.0` with the internal
hostname/IP pointing to gateway.

**This line MUST replace the one for the homeserver and MUST be after the one to your Identity server, if applicable.**

```
location /_matrix {
    proxy_pass http://0.0.0.0:8007/_matrix;
}
```

Typical configuration would look like:
```
server {
    listen 443 ssl;
    server_name example.org;
    
    ...
    
    location /_matrix {
        proxy_pass http://localhost:8007/_matrix;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
}
```

### Configuration
Create a file `mxgwd.yaml` in your current working directory and add the appropriate options.  
You can provide another path using the environment variable `MXGWD_CONFIG_FILE`.

Example of pseudo-config file:
```yaml
# Base configuration key
matrix.client.hosts:

  # A host definition using the default protocol port
  example.org:
    to: <Base URL to the Homeserver Client API>
    toIdentity: <Base URL to the extended Identity server API>
    
    # Endpoints definitions for this host
    endpoints:
      - action: <Well-defined Matrix action, overwriten by method and path below>
        method: <HTTP method to match>
        path: <Start of the URL path to match>
      
        # List of ACLs to be applied to this endpoint
        acls:
          - type: <Type of this ACL>
            target: <Target of this ACL>
            value: <Value to match against for this ACL>
        
          # another ACL
          - type: ...
          
      # Another endpoint definition
      - action: ...
        to: <Specific backend target URL, takes precedence over the Host one>
        acls:
          - ...
          - ...
      
  # Another host definition with a non-default port
  example.org:8443:
    ...
  
  # Another host definition with a hostname including a sub-domain
  another.example.org:
    ...
```

---

Example of a working configuration:
```yaml
matrix.client.hosts:
  synapse.localhost:
    to: 'http://localhost:8008'
    toIdentity: 'http://localhost:8090'
        
    endpoints:
      - path: '/_matrix/client/r0/profile/'
        acls:
          - type: 'whitelist'
            target: 'method'
            value: 'GET'
            
      - action: 'm.room.create'
        acls:
          - type: 'whitelist'
            target: 'group'
            value: 'Admins'
```

This configuration will allow requests to `http://synapse.localhost` or `https://synapse.localhost`.  
Requests will be forwarded to `http://localhost:8008`.  
An extended Identity server at `http://localhost:8090` will be used for groups lookups.

Two endpoint definitions are given:  
1. A generic endpoint matching all HTTP method and URL paths starting with `/_matrix/client/r0/profile/`.  
   One ACL is applied which will only allow requests using a HTTP `GET` method and deny all others.  
   This will restrict all clients (authenticated or not) to edit profiles (theirs or anyone else's).
2. A well-defined Matrix action of creating a room, which will be mapped to the appropriate HTTP method and URL path.  
   One ACL is applied which will only allow requests from authenticated users belonging to the `Admins` group.  
   This will restrict room creation to an opaque group provided by the extended Identity server.

All other requests to `synapse.localhost` will be allowed.

All other requests to any other host will be denied.

### Run
```bash
# Build mxgwd
./gradlew shadowJar

# You should create a configuration file if you haven't already

# Run mxgwd 
java -jar build/libs/mxgwd.jar
```

## Support
### Community
Matrix room: [#mxgwd:kamax.io](https://matrix.to/#/#mxgwd:kamax.io)

### Commercial
See the [contact page](https://www.kamax.io/contact/) on our website.
