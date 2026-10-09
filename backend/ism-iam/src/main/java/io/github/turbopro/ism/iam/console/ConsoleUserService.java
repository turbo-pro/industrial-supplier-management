package io.github.turbopro.ism.iam.console;

import io.github.turbopro.ism.common.api.error.ApiException;
import io.github.turbopro.ism.common.api.error.CommonErrorCode;
import io.github.turbopro.ism.iam.auth.IamErrorCode;
import io.github.turbopro.ism.iam.auth.PasswordPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConsoleUserService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final ConsoleUserMapper mapper;
    private final PasswordEncoder passwords;
    public ConsoleUserService(ConsoleUserMapper mapper,PasswordEncoder passwords){this.mapper=mapper;this.passwords=passwords;}

    public List<ConsoleUserModels.View> users(){
        Map<Long,Set<String>> roles=mapper.roles().stream().collect(Collectors.groupingBy(
                ConsoleUserModels.UserRole::userId,Collectors.mapping(ConsoleUserModels.UserRole::roleCode,Collectors.toSet())));
        return mapper.users().stream().map(row->view(row,roles.getOrDefault(row.id(),Set.of()))).toList();
    }

    @Transactional
    public ConsoleUserModels.View create(ConsoleUserModels.Create command){
        if(!PasswordPolicy.isStrong(command.initialPassword())) throw new ApiException(IamErrorCode.PASSWORD_POLICY);
        long id=RANDOM.nextLong(Long.MAX_VALUE-1)+1;
        try {mapper.insertUser(id,command.username(),command.displayName(),passwords.encode(command.initialPassword()));}
        catch(DuplicateKeyException exception){throw new ApiException(ConsoleUserErrorCode.DUPLICATE);}
        if(mapper.assignRole(id,command.roleCode())!=1) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    @Transactional
    public ConsoleUserModels.View changeStatus(long id,ConsoleUserModels.ChangeStatus command,long actorId){
        if(mapper.lockPlatform()==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        ConsoleUserModels.Row target=mapper.lockUser(id);
        if(target==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(target.version()!=command.version()||target.status().equals(command.status())) throw new ApiException(CommonErrorCode.CONFLICT);
        if("DISABLED".equals(command.status())){
            if(id==actorId) throw new ApiException(ConsoleUserErrorCode.SELF_DISABLE);
            if(mapper.isAdmin(id)>0&&mapper.activeAdmins()<=1) throw new ApiException(ConsoleUserErrorCode.LAST_ADMIN);
        }
        if(mapper.changeStatus(id,command.status(),command.version())!=1) throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.revokeTokens(id,LocalDateTime.now());
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    @Transactional
    public ConsoleUserModels.View changeRole(long id,ConsoleUserModels.ChangeRole command,long actorId){
        if(mapper.lockPlatform()==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        ConsoleUserModels.Row target=mapper.lockUser(id);
        if(target==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(target.version()!=command.version()) throw new ApiException(CommonErrorCode.CONFLICT);
        if(id==actorId) throw new ApiException(ConsoleUserErrorCode.SELF_ROLE_CHANGE);
        boolean admin=mapper.isAdmin(id)>0;
        if(admin=="PLATFORM_ADMIN".equals(command.roleCode())) throw new ApiException(CommonErrorCode.CONFLICT);
        if(admin&&"ACTIVE".equals(target.status())&&mapper.activeAdmins()<=1)
            throw new ApiException(ConsoleUserErrorCode.LAST_ADMIN);
        mapper.clearRoles(id);
        if(mapper.assignRole(id,command.roleCode())!=1) throw new ApiException(CommonErrorCode.VALIDATION_FAILED);
        if(mapper.bumpSessionVersion(id,command.version())!=1) throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.revokeTokens(id,LocalDateTime.now());
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    @Transactional
    public ConsoleUserModels.View resetPassword(long id,ConsoleUserModels.ResetPassword command,long actorId){
        if(mapper.lockPlatform()==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        ConsoleUserModels.Row target=mapper.lockUser(id);
        if(target==null) throw new ApiException(CommonErrorCode.NOT_FOUND);
        if(target.version()!=command.version()) throw new ApiException(CommonErrorCode.CONFLICT);
        if(id==actorId) throw new ApiException(ConsoleUserErrorCode.SELF_PASSWORD_RESET);
        if(!PasswordPolicy.isStrong(command.temporaryPassword()) ||
                passwords.matches(command.temporaryPassword(),mapper.passwordHash(id)))
            throw new ApiException(IamErrorCode.PASSWORD_POLICY);
        if(mapper.resetPassword(id,passwords.encode(command.temporaryPassword()),command.version())!=1)
            throw new ApiException(CommonErrorCode.CONFLICT);
        mapper.revokeTokens(id,LocalDateTime.now());
        return users().stream().filter(user->user.id().equals(Long.toString(id))).findFirst().orElseThrow();
    }

    private ConsoleUserModels.View view(ConsoleUserModels.Row row,Set<String> roles){
        return new ConsoleUserModels.View(Long.toString(row.id()),row.username(),row.displayName(),row.status(),
                row.forcePasswordChange(),row.version(),Set.copyOf(roles));
    }
}
