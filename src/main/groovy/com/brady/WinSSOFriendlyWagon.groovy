package com.brady

import org.apache.maven.wagon.*
import org.apache.maven.wagon.authentication.AuthenticationException
import org.apache.maven.wagon.authorization.AuthorizationException

class WinSSOFriendlyWagon extends StreamWagon {
    @Override
    void fillInputData(
            InputData inputData) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {

    }

    @Override
    void fillOutputData(OutputData outputData) throws TransferFailedException {

    }

    @Override
    protected void openConnectionInternal() throws ConnectionException, AuthenticationException {
        println "our job would be to 'open a connection' to repo ${repository}"
    }

    @Override
    void closeConnection() throws ConnectionException {

    }
}
