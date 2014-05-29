package info.blockchain.wallet.ui;

import android.widget.Toast;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;

import piuk.EventListeners;
import piuk.MyRemoteWallet;
import piuk.blockchain.android.WalletApplication;
import piuk.blockchain.android.WalletApplication.AddAddressCallback;
import piuk.blockchain.android.ui.SuccessCallback;

public class AddressManager {
	private MyRemoteWallet blockchainWallet;
	private WalletApplication application;
	
	public AddressManager(MyRemoteWallet remoteWallet, WalletApplication application) {
		this.blockchainWallet = remoteWallet;
	}	
	
	public void setAddressLabel(final String address, final String label,
			final Runnable checkIfWalletHasUpdatedAndFetchTransactionsFail,
			final Runnable settingLabelFail,
			final Runnable syncingWalletFail) {
		if (blockchainWallet == null)
			return;

		application.checkIfWalletHasUpdatedAndFetchTransactions(blockchainWallet.getTemporyPassword(), new SuccessCallback() {

			@Override
			public void onSuccess() {
				try {
					blockchainWallet.addLabel(address, label);

					new Thread() {
						@Override
						public void run() {
							try {
								blockchainWallet.remoteSave();

								System.out.println("invokeWalletDidChange()");

								EventListeners.invokeWalletDidChange();
							} catch (Exception e) {
								e.printStackTrace(); 

								application.writeException(e);

								application.getHandler().post(syncingWalletFail);
							}
						}
					}.start();
				} catch (Exception e) {
					e.printStackTrace();

					application.getHandler().post(settingLabelFail);
				}
			}

			@Override
			public void onFail() {
				application.getHandler().post(checkIfWalletHasUpdatedAndFetchTransactionsFail);
			}
		});
	}
	
	public void newAddress(final AddAddressCallback callback) {
		final ECKey key = application.getRemoteWallet().generateECKey();			
		final String address = key.toAddress(NetworkParameters.prodNet()).toString();
		final String label = null;
		
		if (blockchainWallet == null) {
			callback.onError("Wallet null.");
			return;
		}

		if (application.isInP2PFallbackMode()) {
			callback.onError("Error saving wallet.");
			return;
		}

		try {
			final boolean success = blockchainWallet.addKey(key, address, label);
			if (success) {
				application.localSaveWallet();

				application.saveWallet(new SuccessCallback() {
					@Override
					public void onSuccess() {
						application.checkIfWalletHasUpdated(blockchainWallet.getTemporyPassword(), false, new SuccessCallback() {
							@Override
							public void onSuccess() {	
								try {
									ECKey key = blockchainWallet.getECKey(address);									
									if (key != null && key.toAddress(NetworkParameters.prodNet()).toString().equals(address)) {
										callback.onSavedAddress(address);
									} else {
										blockchainWallet.removeKey(key);

										callback.onError("WARNING! Wallet saved but address doesn't seem to exist after re-read.");
									}
								} catch (Exception e) {
									blockchainWallet.removeKey(key);

									callback.onError("WARNING! Error checking if ECKey is valid on re-read.");
								}
							}

							@Override
							public void onFail() {
								blockchainWallet.removeKey(key);

								callback.onError("WARNING! Error checking if address was correctly saved.");
							}
						});
					}

					@Override
					public void onFail() {
						blockchainWallet.removeKey(key);

						callback.onError("Error saving wallet");
					}
				});
			} else {
				callback.onError("addKey returned false");
			}

		} catch (Exception e) {
			e.printStackTrace();

			application.writeException(e);

			callback.onError(e.getLocalizedMessage());
		}
	}
}