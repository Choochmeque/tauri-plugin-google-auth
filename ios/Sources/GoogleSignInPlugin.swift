import SimpleGoogleSignIn
import Tauri
import UIKit
import WebKit

class SignInArgs: Decodable {
    let clientId: String
    let scopes: [String]?
    let hostedDomain: String?
    let loginHint: String?
}

class SignOutArgs: Decodable {
    let accessToken: String?
}

class RefreshTokenArgs: Decodable {
    let refreshToken: String
}

class GoogleSignInPlugin: Plugin {
    @objc public func signIn(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(SignInArgs.self)

        DispatchQueue.main.async { [weak self] in
            guard let rootViewController = self?.manager.viewController else {
                invoke.reject("No root view controller found")
                return
            }

            guard let scopes = args.scopes, !scopes.isEmpty else {
                invoke.reject("No scopes found")
                return
            }

            let configuration = GoogleSignInConfiguration(
                clientID: args.clientId
            )

            SimpleGoogleSignIn.shared.configure(configuration: configuration)

            SimpleGoogleSignIn.shared.signIn(
                presentingViewController: rootViewController, scopes: scopes
            ) { result in
                DispatchQueue.main.async {
                    switch result {
                    case .success(let signInResult):
                        var tokenDict: [String: Any] = [
                            "idToken": signInResult.openIdToken,
                            "accessToken": signInResult.accessToken.tokenString,
                            "refreshToken": signInResult.refreshToken ?? "",
                            "scopes": signInResult.grantedScopes ?? []
                        ]

                         if let expirationDate = signInResult.accessToken.expirationDate {
                             tokenDict["expiresAt"] = Int64(expirationDate.timeIntervalSince1970 * 1000)
                         }

                        invoke.resolve(tokenDict)

                    case .failure(let error):
                        invoke.reject(error.localizedDescription)
                    }
                }
            }
        }
    }

    @objc public func signOut(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(SignOutArgs.self)

        DispatchQueue.main.async {
            SimpleGoogleSignIn.shared.signOut(accessToken: args.accessToken) { _ in
                invoke.resolve(["success": true])
            }
        }
    }

    @objc public func refreshToken(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(RefreshTokenArgs.self)
        
         DispatchQueue.main.async {
             SimpleGoogleSignIn.shared.refreshTokens(refreshToken: args.refreshToken) { result in
                 switch result {
                 case .success(let signInResult):
                     var tokenDict: [String: Any] = [
                         "idToken": signInResult.openIdToken,
                         "accessToken": signInResult.accessToken.tokenString,
                         "refreshToken": signInResult.refreshToken ?? "",
                         "scopes": signInResult.grantedScopes ?? []
                     ]

                      if let expirationDate = signInResult.accessToken.expirationDate {
                          tokenDict["expiresAt"] = Int64(expirationDate.timeIntervalSince1970 * 1000)
                      }

                     invoke.resolve(tokenDict)

                 case .failure(let error):
                     invoke.reject(error.localizedDescription)
                 }
             }
         }
    }

    @objc public func handleUrl(_ url: URL) -> Bool {
        return SimpleGoogleSignIn.shared.handleURL(url)
    }
}

@_cdecl("init_plugin_google_auth")
func initPlugin() -> Plugin {
    return GoogleSignInPlugin()
}
