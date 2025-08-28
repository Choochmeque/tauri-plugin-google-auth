import SwiftRs
import Tauri
import UIKit
import WebKit
import GoogleSignIn
import GoogleSignInSwift

class SignInArgs: Decodable {
    let clientId: String
    let scopes: [String]?
    let hostedDomain: String?
    let loginHint: String?
}

class GoogleSignInPlugin: Plugin {
    private var currentSignInCompletion: ((Result<GIDGoogleUser, Error>) -> Void)?
    
    @objc public func signIn(_ invoke: Invoke) throws {
        let args = try invoke.parseArgs(SignInArgs.self)
        
        DispatchQueue.main.async { [weak self] in
            guard let rootViewController = self?.manager.viewController else {
                invoke.reject("No root view controller found")
                return
            }
            
            var configuration = GIDConfiguration(clientID: args.clientId)
            
            GIDSignIn.sharedInstance.configuration = configuration
            
            var additionalScopes: [String] = []
            if let scopes = args.scopes {
                additionalScopes = scopes
            }
            
            self?.currentSignInCompletion = { result in
                switch result {
                case .success(let user):
                    var tokenDict: [String: Any] = [
                        "idToken": user.idToken?.tokenString ?? "",
                        "accessToken": user.accessToken.tokenString,
                        "refreshToken": user.refreshToken.tokenString
                    ]
                    
                    if let expirationDate = user.accessToken.expirationDate {
                        tokenDict["expiresAt"] = Int64(expirationDate.timeIntervalSince1970 * 1000)
                    }
                    
                    invoke.resolve(tokenDict)
                    
                case .failure(let error):
                    invoke.reject(error.localizedDescription)
                }
                
                self?.currentSignInCompletion = nil
            }
            
            GIDSignIn.sharedInstance.signIn(
                withPresenting: rootViewController,
                hint: args.loginHint,
                additionalScopes: additionalScopes
            ) { [weak self] result, error in
                if let error = error {
                    self?.currentSignInCompletion?(.failure(error))
                } else if let result = result {
                    self?.currentSignInCompletion?(.success(result.user))
                } else {
                    self?.currentSignInCompletion?(.failure(NSError(
                        domain: "GoogleSignIn",
                        code: -1,
                        userInfo: [NSLocalizedDescriptionKey: "Unknown error occurred"]
                    )))
                }
            }
        }
    }
    
    @objc public func signOut(_ invoke: Invoke) throws {
        DispatchQueue.main.async {
            GIDSignIn.sharedInstance.signOut()
            invoke.resolve(["success": true])
        }
    }
    
    @objc public func refreshToken(_ invoke: Invoke) throws {
        DispatchQueue.main.async {
            guard let currentUser = GIDSignIn.sharedInstance.currentUser else {
                invoke.reject("No user is currently signed in")
                return
            }
            
            currentUser.refreshTokensIfNeeded { user, error in
                if let error = error {
                    invoke.reject(error.localizedDescription)
                    return
                }
                
                guard let user = user else {
                    invoke.reject("Failed to refresh tokens")
                    return
                }
                
                var tokenDict: [String: Any] = [
                    "idToken": user.idToken?.tokenString ?? "",
                    "accessToken": user.accessToken.tokenString,
                    "refreshToken": user.refreshToken.tokenString
                ]
                
                if let expirationDate = user.accessToken.expirationDate {
                    tokenDict["expiresAt"] = Int64(expirationDate.timeIntervalSince1970 * 1000)
                }
                
                invoke.resolve(tokenDict)
            }
        }
    }
    
    @objc public func handleUrl(_ url: URL) -> Bool {
        return GIDSignIn.sharedInstance.handle(url)
    }
}

@_cdecl("init_plugin_google_auth")
func initPlugin() -> Plugin {
    return GoogleSignInPlugin()
}
